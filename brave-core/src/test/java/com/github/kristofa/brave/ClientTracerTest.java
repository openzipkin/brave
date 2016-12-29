package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import zipkin.Constants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest(AnnotationSubmitter.DefaultClock.class)
public class ClientTracerTest {

    private static final long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private static final String REQUEST_NAME = "requestname";
    private static final long TRACE_ID = 105;
    private static final SpanId PARENT_CONTEXT = SpanId.builder().traceId(TRACE_ID).spanId(103).build();
    private static final Endpoint endpoint = Endpoint.create("serviceName", 80);

    private SpanCollector mockCollector;
    private Span span = Brave.newSpan(SpanId.builder().spanId(TRACE_ID).build());
    private Brave brave;

    @Before
    public void setup() {
        ThreadLocalServerClientAndLocalSpanState.clear();
        mockCollector = mock(SpanCollector.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(START_TIME_MICROSECONDS / 1000);
        PowerMockito.when(System.nanoTime()).thenReturn(0L);

        brave = braveBuilder().build();
    }

    @Test
    public void testSetClientSentNoClientSpan() {
        brave.clientTracer().setClientSent();
        verifyNoMoreInteractions(mockCollector);
    }

    @Test
    public void testSetClientSent() {
        brave.clientSpanThreadBinder().setCurrentSpan(span);
        brave.clientTracer().setClientSent();

        final Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.CLIENT_SEND,
            endpoint
        );
        verifyNoMoreInteractions(mockCollector);

        assertEquals(START_TIME_MICROSECONDS, span.getTimestamp().longValue());
        assertEquals(expectedAnnotation, span.getAnnotations().get(0));
    }

    @Test
    public void testSetClientSentServerAddress() {
        brave.clientSpanThreadBinder().setCurrentSpan(span);

        brave.clientTracer().setClientSent(Endpoint.builder()
            .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).port(9999).serviceName("foobar").build());

        final Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.CLIENT_SEND,
            endpoint
        );
        verifyNoMoreInteractions(mockCollector);

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
        brave.clientSpanThreadBinder().setCurrentSpan(span);

        brave.clientTracer()
            .setClientSent(1 << 24 | 2 << 16 | 3 << 8 | 4, 9999, null);

        assertEquals("unknown", span.getBinary_annotations().get(0).host.service_name);
    }

    @Test
    public void testSetClientReceivedNoClientSpan() {
        brave.clientTracer().setClientReceived();

        verifyNoMoreInteractions(mockCollector);
    }

    @Test
    public void testSetClientReceived() {
        brave.clientSpanThreadBinder().setCurrentSpan(span.setTimestamp(100L));

        brave.clientTracer().setClientReceived();

        final Annotation expectedAnnotation = Annotation.create(
            START_TIME_MICROSECONDS,
            Constants.CLIENT_RECV,
            endpoint
        );

        assertNull(brave.clientSpanThreadBinder().getCurrentClientSpan());

        verify(mockCollector).collect(span);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(START_TIME_MICROSECONDS - span.getTimestamp().longValue(), span.getDuration().longValue());
        assertEquals(expectedAnnotation, span.getAnnotations().get(0));
    }

    @Test
    public void testStartNewSpanSampleFalse() {
        brave.serverSpanThreadBinder().setCurrentSpan(ServerSpan.NOT_SAMPLED);

        assertNull(brave.clientTracer().startNewSpan(REQUEST_NAME));

        verifyNoMoreInteractions(mockCollector);
    }

    @Test
    public void testStartNewSpanSampleNullNotPartOfExistingSpan() {
        brave.serverSpanThreadBinder().setCurrentSpan(ServerSpan.EMPTY);

        SpanId newSpanId = brave.clientTracer().startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertNull(newSpanId.nullableParentId());

        assertThat(brave.clientSpanThreadBinder().getCurrentClientSpan().getName())
            .isEqualTo(REQUEST_NAME);

        verifyNoMoreInteractions(mockCollector);
    }

    @Test
    public void testStartNewSpanSampleTruePartOfExistingSpan() {
        final ServerSpan parentSpan = ServerSpan.create(Brave.newSpan(PARENT_CONTEXT), "name");
        brave.serverSpanThreadBinder().setCurrentSpan(parentSpan);

        SpanId newContext = brave.clientTracer().startNewSpan(REQUEST_NAME);
        assertNotNull(newContext);
        assertEquals(TRACE_ID, newContext.traceId);
        assertEquals(PARENT_CONTEXT.spanId, newContext.parentId);

        assertThat(brave.clientSpanThreadBinder().getCurrentClientSpan().getName())
            .isEqualTo(REQUEST_NAME);

        verifyNoMoreInteractions(mockCollector);
    }

    @Test
    public void testSamplerFalse() {
        brave = braveBuilder().traceSampler(Sampler.NEVER_SAMPLE).build();
        brave.serverSpanThreadBinder().setCurrentSpan(ServerSpan.EMPTY);

        assertNull(brave.clientTracer().startNewSpan(REQUEST_NAME));

        assertThat(brave.clientSpanThreadBinder().getCurrentClientSpan()).isNull();

        verifyNoMoreInteractions(mockCollector);
    }

    @Test
    public void setClientReceived_usesPreciseDuration() {
        brave.clientSpanThreadBinder().setCurrentSpan(span.setTimestamp(START_TIME_MICROSECONDS));

        PowerMockito.when(System.nanoTime()).thenReturn(500000L);

        brave.clientTracer().setClientReceived();

        verify(mockCollector).collect(span);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(500L, span.getDuration().longValue());
    }

    /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
    @Test
    public void setClientReceived_lessThanMicrosRoundUp() {
        brave.clientSpanThreadBinder().setCurrentSpan(span.setTimestamp(START_TIME_MICROSECONDS));

        PowerMockito.when(System.nanoTime()).thenReturn(500L);

        brave.clientTracer().setClientReceived();

        verify(mockCollector).collect(span);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(1L, span.getDuration().longValue());
    }

    @Test
    public void startNewSpan_whenParentHas128bitTraceId() {
        ServerSpan parentSpan = ServerSpan.create(
            Brave.newSpan(PARENT_CONTEXT.toBuilder().traceIdHigh(3).build()), "name");
        brave.serverSpanThreadBinder().setCurrentSpan(parentSpan);

        SpanId newContext = brave.clientTracer().startNewSpan(REQUEST_NAME);
        assertEquals(3, newContext.traceIdHigh);
        assertEquals(TRACE_ID, newContext.traceId);
    }

    @Test
    public void startNewSpan_rootSpanWith64bitTraceId() {
        SpanId newContext = brave.clientTracer().startNewSpan(REQUEST_NAME);
        assertThat(newContext.traceIdHigh).isZero();
        assertThat(newContext.traceId).isNotZero();
    }

    @Test
    public void startNewSpan_rootSpanWith128bitTraceId() {
        brave = braveBuilder().traceId128Bit(true).build();

        SpanId newContext = brave.clientTracer().startNewSpan(REQUEST_NAME);
        assertThat(newContext.traceIdHigh).isNotZero();
        assertThat(newContext.traceId).isNotZero();
    }

    Brave.Builder braveBuilder() {
        return new Brave.Builder(endpoint).spanCollector(mockCollector);
    }
}
