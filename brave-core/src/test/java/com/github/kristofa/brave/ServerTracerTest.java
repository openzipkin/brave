package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.class)
public class ServerTracerTest {

    private static final long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private static final long TRACE_ID = 1;
    private static final SpanId SPAN_ID = SpanId.builder().traceId(TRACE_ID).spanId(2).parentId(3L).build();
    private static final String SPAN_NAME = "span name";

    private ServerTracer serverTracer;
    private ServerSpanState mockServerSpanState;
    private SpanCollector mockSpanCollector;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndpoint = Endpoint.create("service", 0);
    private Random mockRandom;
    private Sampler mockSampler;

    @Before
    public void setup() {
        mockServerSpanState = mock(ServerSpanState.class);
        mockSpanCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);
        mockServerSpan = mock(ServerSpan.class);

        mockRandom = mock(Random.class);
        mockSampler = mock(Sampler.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
        serverTracer = ServerTracer.builder()
            .state(mockServerSpanState)
            .randomGenerator(mockRandom)
            .spanCollector(mockSpanCollector)
            .traceSampler(mockSampler)
            .clock(AnnotationSubmitter.DefaultClock.INSTANCE)
            .traceId128Bit(false)
            .build();
    }

    @Test
    public void testClearCurrentSpan() {
        serverTracer.clearCurrentSpan();
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateCurrentTrace() {
        serverTracer.setStateCurrentTrace(SPAN_ID, SPAN_NAME);
        ServerSpan expectedServerSpan = ServerSpan.create(SPAN_ID, SPAN_NAME);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateNoTracing() {
        serverTracer.setStateNoTracing();
        verify(mockServerSpanState).setCurrentServerSpan(ServerSpan.NOT_SAMPLED);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateUnknownSamplerTrue() {

        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        serverTracer.setStateUnknown(SPAN_NAME);
        ServerSpan expectedServerSpan = ServerSpan.create(
            SpanId.builder().spanId(TRACE_ID).build(), SPAN_NAME);

        final InOrder inOrder = inOrder(mockSampler, mockRandom, mockServerSpanState);

        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockSampler).isSampled(TRACE_ID);
        inOrder.verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockRandom);
    }

    @Test
    public void testSetStateUnknownSamplerTrue_128Bit() {
        serverTracer = new AutoValue_ServerTracer.Builder(serverTracer)
            .traceId128Bit(true).build();

        when(mockRandom.nextLong()).thenReturn(TRACE_ID, TRACE_ID + 1);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        serverTracer.setStateUnknown(SPAN_NAME);
        ServerSpan expectedServerSpan = ServerSpan.create(SpanId.builder()
            .traceIdHigh(TRACE_ID + 1)
            .traceId(TRACE_ID)
            .spanId(TRACE_ID).build(), SPAN_NAME);

        final InOrder inOrder = inOrder(mockSampler, mockRandom, mockServerSpanState);

        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockSampler).isSampled(TRACE_ID);
        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockRandom);
    }

    @Test
    public void testSetStateUnknownSamplerFalse() {
        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(false);

        serverTracer.setStateUnknown(SPAN_NAME);

        final InOrder inOrder = inOrder(mockSampler, mockRandom, mockServerSpanState);

        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockSampler).isSampled(TRACE_ID);
        inOrder.verify(mockServerSpanState).setCurrentServerSpan(ServerSpan.NOT_SAMPLED);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockRandom);
    }

    @Test
    public void testSetServerReceivedNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpan.getSpan()).thenReturn(null);
        serverTracer.setServerReceived();
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetServerReceived() {
        Span serverRecv = new Span();
        when(mockServerSpan.getSpan()).thenReturn(serverRecv);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.endpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerReceived();

        final Annotation expectedAnnotation = Annotation.create(
            CURRENT_TIME_MICROSECONDS,
            Constants.SERVER_RECV,
            mockEndpoint
        );

        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).endpoint();

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);

        assertEquals(CURRENT_TIME_MICROSECONDS, serverRecv.getTimestamp().longValue());
        assertEquals(expectedAnnotation, serverRecv.getAnnotations().get(0));
    }

    @Test
    public void testSetServerReceivedSentClientAddress() {
        Span serverRecv = new Span();
        when(mockServerSpan.getSpan()).thenReturn(serverRecv);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.endpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerReceived(Endpoint.builder()
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4)
            .port(9999)
            .serviceName("foobar").build());


        final Annotation expectedAnnotation = Annotation.create(
            CURRENT_TIME_MICROSECONDS,
            Constants.SERVER_RECV,
            mockEndpoint
        );

        verify(mockServerSpanState, times(2)).getCurrentServerSpan();
        verify(mockServerSpanState).endpoint();

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);

        assertEquals(CURRENT_TIME_MICROSECONDS, serverRecv.getTimestamp().longValue());
        assertEquals(expectedAnnotation, serverRecv.getAnnotations().get(0));

        BinaryAnnotation serverAddress = BinaryAnnotation.address(
            Constants.CLIENT_ADDR,
            Endpoint.builder().serviceName("foobar").ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(9999).build()
        );
        assertEquals(serverAddress, serverRecv.getBinary_annotations().get(0));
    }

    @Test
    public void testSetServerReceivedSentClientAddress_noServiceName() {
        Span serverRecv = new Span();
        when(mockServerSpan.getSpan()).thenReturn(serverRecv);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.endpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerReceived(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

        assertEquals("unknown", serverRecv.getBinary_annotations().get(0).host.service_name);
    }

    @Test
    public void testSetServerSendShouldNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpan.getSpan()).thenReturn(null);
        serverTracer.setServerSend();
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockSpan);
    }

    @Test
    public void testSetServerSend() {
        Span serverSend = new Span().setName("foo").setTimestamp(100L);
        when(mockServerSpan.getSpan()).thenReturn(serverSend);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.endpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerSend();

        final Annotation expectedAnnotation = Annotation.create(
            CURRENT_TIME_MICROSECONDS,
            Constants.SERVER_SEND,
            mockEndpoint
        );

        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).endpoint();

        verify(mockSpanCollector).collect(serverSend);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);

        assertEquals(CURRENT_TIME_MICROSECONDS - serverSend.getTimestamp().longValue(), serverSend.getDuration().longValue());
        assertEquals(expectedAnnotation, serverSend.getAnnotations().get(0));
    }

    @Test
    public void setServerSend_skipsDurationWhenNoTimestamp() {
        Span finished = new Span().setName("foo"); // duration unset due to client-originated trace
        when(mockServerSpan.getSpan()).thenReturn(finished);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);

        serverTracer.setServerSend();

        verify(mockSpanCollector).collect(finished);
        verifyNoMoreInteractions(mockSpanCollector);

        assertThat(finished.getDuration()).isNull();
    }

    @Test
    public void setServerSend_usesPreciseDuration() {
        Span finished = new Span().setName("foo").setTimestamp(1000L); // set in start span
        finished.startTick = 500000L; // set in start span
        when(mockServerSpan.getSpan()).thenReturn(finished);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);

        PowerMockito.when(System.nanoTime()).thenReturn(1000000L);

        serverTracer.setServerSend();

        verify(mockSpanCollector).collect(finished);
        verifyNoMoreInteractions(mockSpanCollector);

        assertEquals(500L, finished.getDuration().longValue());
    }

    /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
    @Test
    public void setServerSend_lessThanMicrosRoundUp() {
        Span finished = new Span().setName("foo").setTimestamp(1000L); // set in start span
        finished.startTick = 500L; // set in start span
        when(mockServerSpan.getSpan()).thenReturn(finished);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);

        PowerMockito.when(System.nanoTime()).thenReturn(1000L);

        serverTracer.setServerSend();

        verify(mockSpanCollector).collect(finished);
        verifyNoMoreInteractions(mockSpanCollector);

        assertEquals(1L, finished.getDuration().longValue());
    }
}
