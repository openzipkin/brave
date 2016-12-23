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
@PrepareForTest(AnnotationSubmitter.DefaultClock.class)
public class ClientTracerTest {

    private static final long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private static final String REQUEST_NAME = "requestname";
    private static final long TRACE_ID = 105;
    private static final SpanId PARENT_SPAN_ID = SpanId.builder().traceId(TRACE_ID).spanId(103).build();

    private ServerClientAndLocalSpanState state = new TestServerClientAndLocalSpanStateCompilation();
    private Random mockRandom;
    private SpanCollector mockCollector;
    private ClientTracer clientTracer;
    private Sampler mockSampler;
    private Span span = Span.create(SpanId.builder().spanId(TRACE_ID).build());

    @Before
    public void setup() {
        mockSampler = mock(Sampler.class);
        mockRandom = mock(Random.class);
        mockCollector = mock(SpanCollector.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(START_TIME_MICROSECONDS / 1000);
        PowerMockito.when(System.nanoTime()).thenReturn(0L);

        clientTracer = ClientTracer.builder()
            .state(state)
            .randomGenerator(mockRandom)
            .spanCollector(mockCollector)
            .traceSampler(mockSampler)
            .clock(new AnnotationSubmitter.DefaultClock())
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
        state.setCurrentClientSpan(span);
        clientTracer.setClientSent();

        final Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.CLIENT_SEND,
            state.endpoint()
        );
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(START_TIME_MICROSECONDS, span.getTimestamp().longValue());
        assertEquals(expectedAnnotation, span.getAnnotations().get(0));
    }

    @Test
    public void testSetClientSentServerAddress() {
        state.setCurrentClientSpan(span);

        clientTracer.setClientSent(Endpoint.builder()
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(9999).serviceName("foobar").build());

        final Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.CLIENT_SEND,
            state.endpoint()
        );
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(START_TIME_MICROSECONDS, span.getTimestamp().longValue());
        assertEquals(expectedAnnotation, span.getAnnotations().get(0));

        BinaryAnnotation serverAddress = BinaryAnnotation.address(
            Constants.SERVER_ADDR,
            Endpoint.builder().serviceName("foobar").ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(9999).build()
        );
        assertEquals(serverAddress, span.getBinary_annotations().get(0));
    }

    @Test
    public void testSetClientSentServerAddress_noServiceName() {
        state.setCurrentClientSpan(span);

        clientTracer.setClientSent(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

        assertEquals("unknown", span.getBinary_annotations().get(0).host.service_name);
    }

    @Test
    public void testSetClientReceivedNoClientSpan() {
        state.setCurrentClientSpan(null);

        clientTracer.setClientReceived();

        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testSetClientReceived() {
        state.setCurrentClientSpan(span.setName("foo").setTimestamp(100L));

        clientTracer.setClientReceived();

        final Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.CLIENT_RECV,
            state.endpoint()
        );

        assertNull(state.getCurrentClientSpan());
        assertEquals(state.endpoint(), state.endpoint());

        verify(mockCollector).collect(span);
        verifyNoMoreInteractions(mockCollector, mockSampler);

        assertEquals(START_TIME_MICROSECONDS - span.getTimestamp().longValue(), span.getDuration().longValue());
        assertEquals(expectedAnnotation, span.getAnnotations().get(0));
    }

    @Test
    public void testStartNewSpanSampleFalse() {
        state.setCurrentServerSpan(ServerSpan.NOT_SAMPLED);

        assertNull(clientTracer.startNewSpan(REQUEST_NAME));

        verifyNoMoreInteractions(mockCollector, mockSampler);
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

        assertEquals(span.setName(REQUEST_NAME), state.getCurrentClientSpan());

        verify(mockSampler).isSampled(TRACE_ID);

        verifyNoMoreInteractions(mockCollector, mockSampler);
    }

    @Test
    public void testStartNewSpanSampleTruePartOfExistingSpan() {
        final ServerSpan parentSpan = ServerSpan.create(PARENT_SPAN_ID, "name");
        state.setCurrentServerSpan(parentSpan);
        when(mockRandom.nextLong()).thenReturn(1L);

        final SpanId newContext = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newContext);
        assertEquals(TRACE_ID, newContext.traceId);
        assertEquals(1L, newContext.spanId);
        assertEquals(PARENT_SPAN_ID.spanId, newContext.parentId);

        assertEquals(Span.create(newContext).setName(REQUEST_NAME), state.getCurrentClientSpan());

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
        state.setCurrentClientSpan(span.setName("foo").setTimestamp(START_TIME_MICROSECONDS));

        PowerMockito.when(System.nanoTime()).thenReturn(500000L);

        clientTracer.setClientReceived();

        verify(mockCollector).collect(span);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(500L, span.getDuration().longValue());
    }

    /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
    @Test
    public void setClientReceived_lessThanMicrosRoundUp() {
        state.setCurrentClientSpan(span.setName("foo").setTimestamp(START_TIME_MICROSECONDS));

        PowerMockito.when(System.nanoTime()).thenReturn(500L);

        clientTracer.setClientReceived();

        verify(mockCollector).collect(span);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(1L, span.getDuration().longValue());
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
        clientTracer = clientTracer.toBuilder().traceId128Bit(true).build();
        when(mockRandom.nextLong()).thenReturn(TRACE_ID, TRACE_ID + 1);
        when(mockSampler.isSampled(TRACE_ID)).thenReturn(true);

        SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertEquals(TRACE_ID + 1, newSpanId.traceIdHigh);
        assertEquals(TRACE_ID, newSpanId.traceId);
    }
}
