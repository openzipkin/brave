package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

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

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private final static long TRACE_ID = 1L;
    private final static long SPAN_ID = 2L;
    private final static Long PARENT_SPANID = 3L;
    private final static String SPAN_NAME = "span name";

    private ServerTracer serverTracer;
    private ServerSpanState mockServerSpanState;
    private SpanCollector mockSpanCollector;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndpoint;
    private Random mockRandom;
    private Sampler mockSampler;

    @Before
    public void setup() {
        mockServerSpanState = mock(ServerSpanState.class);
        mockSpanCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);
        mockServerSpan = mock(ServerSpan.class);

        mockEndpoint = mock(Endpoint.class);
        mockRandom = mock(Random.class);
        mockSampler = mock(Sampler.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
        serverTracer = ServerTracer.builder()
            .state(mockServerSpanState)
            .randomGenerator(mockRandom)
            .spanCollector(mockSpanCollector)
            .traceSampler(mockSampler).build();
    }

    @Test
    public void testClearCurrentSpan() {
        serverTracer.clearCurrentSpan();
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateCurrentTrace() {
        serverTracer.setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPANID, SPAN_NAME);
        final ServerSpan expectedServerSpan = ServerSpan.create(TRACE_ID, SPAN_ID, PARENT_SPANID, SPAN_NAME);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateNoTracing() {
        serverTracer.setStateNoTracing();
        final ServerSpan expectedServerSpan = ServerSpan.create(false);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateUnknownSamplerTrue() {

        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        serverTracer.setStateUnknown(SPAN_NAME);
        final ServerSpan expectedServerSpan = ServerSpan.create(TRACE_ID, TRACE_ID, null, SPAN_NAME);

        final InOrder inOrder = inOrder(mockSampler, mockRandom, mockServerSpanState);

        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockSampler).isSampled(TRACE_ID);
        inOrder.verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockRandom);
    }

    @Test
    public void testSetStateUnknownSamplerFalse() {

        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(false);

        final ServerSpan expectedServerSpan = ServerSpan.create(false);

        serverTracer.setStateUnknown(SPAN_NAME);

        final InOrder inOrder = inOrder(mockSampler, mockRandom, mockServerSpanState);

        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockSampler).isSampled(TRACE_ID);
        inOrder.verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);

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
            zipkinCoreConstants.SERVER_RECV,
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
        serverTracer.setServerReceived(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, "foobar");


        final Annotation expectedAnnotation = Annotation.create(
            CURRENT_TIME_MICROSECONDS,
            zipkinCoreConstants.SERVER_RECV,
            mockEndpoint
        );

        verify(mockServerSpanState, times(2)).getCurrentServerSpan();
        verify(mockServerSpanState).endpoint();

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);

        assertEquals(CURRENT_TIME_MICROSECONDS, serverRecv.getTimestamp().longValue());
        assertEquals(expectedAnnotation, serverRecv.getAnnotations().get(0));

        BinaryAnnotation serverAddress = BinaryAnnotation.address(
            zipkinCoreConstants.CLIENT_ADDR,
            Endpoint.create("foobar", 1 << 24 | 2 << 16 | 3 << 8 | 4, 9999)
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
        Span serverSend = new Span().setTimestamp(100L);
        when(mockServerSpan.getSpan()).thenReturn(serverSend);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.endpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerSend();

        final Annotation expectedAnnotation = Annotation.create(
            CURRENT_TIME_MICROSECONDS,
            zipkinCoreConstants.SERVER_SEND,
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

}
