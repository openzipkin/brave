package com.github.kristofa.brave;

import com.github.kristofa.brave.example.TestServerClientAndLocalSpanStateCompilation;
import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.Random;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import zipkin.Constants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.class)
public class ClientTracerTest {

    private static final long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private static final String REQUEST_NAME = "requestname";
    private static final long TRACE_ID = 105;
    private static final SpanId PARENT_SPAN_ID = SpanId.builder().traceId(TRACE_ID).spanId(103).build();

    private ServerClientAndLocalSpanState state = new TestServerClientAndLocalSpanStateCompilation();
    private Random mockRandom;
    private SpanCollector mockCollector;
    private ClientTracer clientTracer;
    private Span mockSpan;
    private Sampler mockSampler;

    @Before
    public void setup() {
        mockSampler = mock(Sampler.class);
        mockRandom = mock(Random.class);
        mockCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
        clientTracer = ClientTracer.builder()
            .state(state)
            .randomGenerator(mockRandom)
            .spanCollector(mockCollector)
            .traceSampler(mockSampler)
            .clock(AnnotationSubmitter.DefaultClock.INSTANCE)
            .traceId128Bit(false)
            .build();
    }

    @Test
    public void testSetClientSentNoClientSpan() {
        state.setCurrentClientSpan(null);
        clientTracer.setClientSent();
        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testSetClientSent() {
        Span clientSent = new Span();
        state.setCurrentClientSpan(clientSent);
        clientTracer.setClientSent();

        final Annotation expectedAnnotation = Annotation.create(
            CURRENT_TIME_MICROSECONDS,
            Constants.CLIENT_SEND,
            state.endpoint()
        );
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(CURRENT_TIME_MICROSECONDS, clientSent.getTimestamp().longValue());
        assertEquals(expectedAnnotation, clientSent.getAnnotations().get(0));
    }

    @Test
    public void testSetClientSentServerAddress() {
        Span clientSent = new Span();
        state.setCurrentClientSpan(clientSent);

        clientTracer.setClientSent(Endpoint.builder()
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(9999).serviceName("foobar").build());

        final Annotation expectedAnnotation = Annotation.create(
            CURRENT_TIME_MICROSECONDS,
            Constants.CLIENT_SEND,
            state.endpoint()
        );
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(CURRENT_TIME_MICROSECONDS, clientSent.getTimestamp().longValue());
        assertEquals(expectedAnnotation, clientSent.getAnnotations().get(0));

        BinaryAnnotation serverAddress = BinaryAnnotation.address(
            Constants.SERVER_ADDR,
            Endpoint.builder().serviceName("foobar").ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(9999).build()
        );
        assertEquals(serverAddress, clientSent.getBinary_annotations().get(0));
    }

    @Test
    public void testSetClientSentServerAddress_noServiceName() {
        Span clientSent = new Span();
        state.setCurrentClientSpan(clientSent);

        clientTracer.setClientSent(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

        assertEquals("unknown", clientSent.getBinary_annotations().get(0).host.service_name);
    }

    @Test
    public void testSetClientReceivedNoClientSpan() {
        state.setCurrentClientSpan(null);

        clientTracer.setClientReceived();

        verifyNoMoreInteractions(mockSpan, mockCollector, mockSampler);
    }

    @Test
    public void testSetClientReceived() {
        Span clientRecv = new Span().setName("foo").setTimestamp(100L);
        state.setCurrentClientSpan(clientRecv);

        clientTracer.setClientReceived();

        final Annotation expectedAnnotation = Annotation.create(
            CURRENT_TIME_MICROSECONDS,
            Constants.CLIENT_RECV,
            state.endpoint()
        );

        assertNull(state.getCurrentClientSpan());
        assertEquals(state.endpoint(), state.endpoint());

        verify(mockCollector).collect(clientRecv);
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(CURRENT_TIME_MICROSECONDS - clientRecv.getTimestamp().longValue(), clientRecv.getDuration().longValue());
        assertEquals(expectedAnnotation, clientRecv.getAnnotations().get(0));
    }

    @Test
    public void testStartNewSpanSampleFalse() {
        state.setCurrentServerSpan(ServerSpan.NOT_SAMPLED);

        assertNull(clientTracer.startNewSpan(REQUEST_NAME));

        verifyNoMoreInteractions(mockSpan, mockCollector, mockSampler);
    }

    @Test
    public void testStartNewSpanSampleNullNotPartOfExistingSpan() {
        state.setCurrentServerSpan(ServerSpan.EMPTY);

        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        final SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertEquals(TRACE_ID, newSpanId.traceId);
        assertEquals(TRACE_ID, newSpanId.spanId);
        assertNull(newSpanId.nullableParentId());

        assertEquals(
                Span.fromSpanId(SpanId.builder().spanId(TRACE_ID).build()).setName(REQUEST_NAME),
                state.getCurrentClientSpan()
        );

        verify(mockSampler).isSampled(TRACE_ID);

        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testStartNewSpanSampleTruePartOfExistingSpan() {
        final ServerSpan parentSpan = ServerSpan.create(PARENT_SPAN_ID, "name");
        state.setCurrentServerSpan(parentSpan);
        when(mockRandom.nextLong()).thenReturn(1L);

        final SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertEquals(TRACE_ID, newSpanId.traceId);
        assertEquals(1L, newSpanId.spanId);
        assertEquals(PARENT_SPAN_ID.spanId, newSpanId.parentId);

        assertEquals(
                Span.fromSpanId(newSpanId).setName(REQUEST_NAME),
                state.getCurrentClientSpan()
        );

        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testSamplerFalse() {
        state.setCurrentServerSpan(ServerSpan.EMPTY);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(false);
        when(mockRandom.nextLong()).thenReturn(TRACE_ID);

        assertNull(clientTracer.startNewSpan(REQUEST_NAME));

        verify(mockSampler).isSampled(TRACE_ID);

        assertNull(state.getCurrentClientSpan());
        assertEquals(state.endpoint(), state.endpoint());

        verifyNoMoreInteractions(mockSampler, mockCollector);
    }

    @Test
    public void setClientReceived_usesPreciseDuration() {
        Span finished = new Span().setName("foo").setTimestamp(1000L); // set in start span
        finished.startTick = 500000L; // set in start span
        state.setCurrentClientSpan(finished);

        PowerMockito.when(System.nanoTime()).thenReturn(1000000L);

        clientTracer.setClientReceived();

        verify(mockCollector).collect(finished);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(500L, finished.getDuration().longValue());
    }

    /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
    @Test
    public void setClientReceived_lessThanMicrosRoundUp() {
        Span finished = new Span().setName("foo").setTimestamp(1000L); // set in start span
        finished.startTick = 500L; // set in start span
        state.setCurrentClientSpan(finished);

        PowerMockito.when(System.nanoTime()).thenReturn(1000L);

        clientTracer.setClientReceived();

        verify(mockCollector).collect(finished);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(1L, finished.getDuration().longValue());
    }

    @Test
    public void startNewSpan_whenParentHas128bitTraceId() {
        ServerSpan parentSpan = ServerSpan.create(
            PARENT_SPAN_ID.toBuilder().traceIdHigh(3).build(), "name");
        state.setCurrentServerSpan(parentSpan);
        when(mockRandom.nextLong()).thenReturn(1L);

        SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertEquals(3, newSpanId.traceIdHigh);
        assertEquals(TRACE_ID, newSpanId.traceId);
    }

    @Test
    public void startNewSpan_rootSpanWith64bitTraceId() {
        when(mockRandom.nextLong()).thenReturn(TRACE_ID);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertEquals(0, newSpanId.traceIdHigh);
        assertEquals(TRACE_ID, newSpanId.traceId);
    }

    @Test
    public void startNewSpan_rootSpanWith128bitTraceId() {
        clientTracer = new AutoValue_ClientTracer.Builder(clientTracer)
            .traceId128Bit(true).build();
        when(mockRandom.nextLong()).thenReturn(TRACE_ID, TRACE_ID + 1);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertEquals(TRACE_ID + 1, newSpanId.traceIdHigh);
        assertEquals(TRACE_ID, newSpanId.traceId);
    }
}
