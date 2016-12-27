package com.github.kristofa.brave;

import static com.github.kristofa.brave.internal.DefaultSpanCodec.toZipkin;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.github.kristofa.brave.internal.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import zipkin.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(PowerMockRunner.class)
// Added to declutter console: tells power mock not to mess with implicit classes we aren't testing
@PowerMockIgnore({"org.apache.logging.*", "javax.script.*"})
@PrepareForTest(AnnotationSubmitter.DefaultClock.class)
public class LocalTracerTest {
    private static final long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;

    private static final long TRACE_ID = 105;
    private static final SpanId PARENT_CONTEXT = SpanId.builder().traceId(TRACE_ID).spanId(103).build();
    private static final String COMPONENT_NAME = "componentname";
    private static final String OPERATION_NAME = "operationname";
    private static final Endpoint endpoint = Endpoint.create("serviceName", 80);

    private Reporter<zipkin.Span> mockReporter;
    private Brave brave;
    private Span span = Span.create(SpanId.builder().spanId(TRACE_ID).build());

    @Before
    public void setup() {
        ThreadLocalServerClientAndLocalSpanState.clear();
        mockReporter = mock(Reporter.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(START_TIME_MICROSECONDS / 1000);
        PowerMockito.when(System.nanoTime()).thenReturn(0L);

        brave = braveBuilder().build();
    }

    /**
     * When a local span is started without a timestamp, microseconds and a tick are recorded for
     * duration calculation. A binary annotation is added for search by component.
     * <p>
     * <p/>Ex.
     * <pre>
     * localTracer.startNewSpan(component, operation); // internally time and nanos are recorded
     * </pre>
     */
    @Test
    public void startNewSpan() {
        brave.serverSpanThreadBinder().setCurrentSpan(ServerSpan.create(PARENT_CONTEXT, "name"));

        PowerMockito.when(System.nanoTime()).thenReturn(500L);

        SpanId newContext = brave.localTracer().startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertThat(newContext).isEqualTo(
            PARENT_CONTEXT.toBuilder()
                .parentId(PARENT_CONTEXT.spanId)
                .spanId(newContext.spanId)
                .build()
        );

        Span started = brave.localSpanThreadBinder().getCurrentLocalSpan();

        assertEquals(START_TIME_MICROSECONDS, started.getTimestamp().longValue());
        assertEquals("lc", started.getBinary_annotations().get(0).getKey());
        assertEquals(COMPONENT_NAME, new String(started.getBinary_annotations().get(0).getValue(), Util.UTF_8));
        assertThat(started.getBinary_annotations().get(0).host).isEqualTo(endpoint);
        assertEquals(OPERATION_NAME, started.getName());
    }

    /**
     * When a span is started with a timestamp, we can't use nanotime for duration as we don't
     * know the nanotime value for that timestamp.
     * <p>
     * <p/>Ex.
     * <pre>
     * localTracer.startNewSpan(component, operation, startTime);
     *
     * </pre>
     */
    @Test
    public void startSpan_userSuppliedTimestamp() {
        brave.serverSpanThreadBinder().setCurrentSpan(ServerSpan.create(PARENT_CONTEXT, "name"));

        brave.localTracer().startNewSpan(COMPONENT_NAME, OPERATION_NAME, 1000L);

        Span started = brave.localSpanThreadBinder().getCurrentLocalSpan();
        assertEquals(1000L, started.getTimestamp().longValue());
    }

    @Test
    public void startSpan_unsampled() {
        brave = braveBuilder().traceSampler(Sampler.NEVER_SAMPLE).build();

        assertNull(brave.localTracer().startNewSpan(COMPONENT_NAME, OPERATION_NAME));
    }

    /**
     * <p>
     * <p/>Ex.
     * <pre>
     * localTracer.startSpan(component, operation); // internally nanos is recorded with system time.
     * ...
     * localTracer.finishSpan(); // above nanos is used to make a precise duration
     * </pre>
     */
    @Test
    public void finishSpan() {
        brave.localSpanThreadBinder().setCurrentSpan(
            span.setName("foo").setTimestamp(START_TIME_MICROSECONDS)
        );

        PowerMockito.when(System.nanoTime()).thenReturn(500000L);

        brave.localTracer().finishSpan();

        verify(mockReporter).report(toZipkin(span));
        verifyNoMoreInteractions(mockReporter);

        assertEquals(500L, span.getDuration().longValue());
    }

    /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
    @Test
    public void finishSpan_lessThanMicrosRoundUp() {
        brave.localSpanThreadBinder().setCurrentSpan(
            span.setName("foo").setTimestamp(START_TIME_MICROSECONDS)
        );

        PowerMockito.when(System.nanoTime()).thenReturn(50L);

        brave.localTracer().finishSpan();

        verify(mockReporter).report(toZipkin(span));
        verifyNoMoreInteractions(mockReporter);

        assertEquals(1L, span.getDuration().longValue());
    }

    @Test
    public void startSpan_with_inheritable_nested_local_spans() {
        InheritableServerClientAndLocalSpanState state =
            new InheritableServerClientAndLocalSpanState(Endpoint.create("test-service", 127 << 24 | 1));
        brave = new Brave.Builder(state).reporter(mockReporter).build();
        LocalTracer localTracer = brave.localTracer();

        assertNull(localTracer.maybeParent());
        state.setCurrentServerSpan(ServerSpan.create(PARENT_CONTEXT, "name"));

        SpanId span1 = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(PARENT_CONTEXT.toBuilder().spanId(span1.spanId).parentId(PARENT_CONTEXT.spanId).build(), span1);
        assertEquals(span1.spanId, localTracer.maybeParent().spanId);

        SpanId span2 = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(PARENT_CONTEXT.toBuilder().spanId(span2.spanId).parentId(span1.spanId).build(), span2);
        assertEquals(span2.spanId, localTracer.maybeParent().spanId);

        assertEquals(span2.spanId, state.getCurrentLocalSpan().getId());
        localTracer.finishSpan();
        assertEquals(span1.spanId, state.getCurrentLocalSpan().getId());
        localTracer.finishSpan();
        assertNull(state.getCurrentLocalSpan());
    }

    @Test
    public void startNewSpan_whenParentHas128bitTraceId() {
        ServerSpan parentSpan = ServerSpan.create(
            PARENT_CONTEXT.toBuilder().traceIdHigh(3).build(), "name");
        brave.serverSpanThreadBinder().setCurrentSpan(parentSpan);

        SpanId newContext = brave.localTracer().startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(3, newContext.traceIdHigh);
        assertEquals(TRACE_ID, newContext.traceId);
    }

    @Test
    public void startNewSpan_rootSpanWith64bitTraceId() {
        SpanId newContext = brave.localTracer().startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertThat(newContext.traceIdHigh).isZero();
        assertThat(newContext.traceId).isNotZero();
    }

    @Test
    public void startNewSpan_rootSpanWith128bitTraceId() {
        brave = braveBuilder().traceId128Bit(true).build();

        SpanId newContext = brave.localTracer().startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertThat(newContext.traceIdHigh).isNotZero();
        assertThat(newContext.traceId).isNotZero();
    }

    Brave.Builder braveBuilder() {
        return new Brave.Builder(endpoint).reporter(mockReporter);
    }
}
