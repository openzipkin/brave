package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Random;

import com.github.kristofa.brave.example.TestServerClientAndLocalSpanStateCompilation;
import com.github.kristofa.brave.internal.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import zipkin.reporter.Reporter;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.DefaultClock.class)
public class LocalTracerTest {
    private static final long START_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;

    private static final long TRACE_ID = 105;
    private static final SpanId PARENT_SPAN_ID = SpanId.builder().traceId(TRACE_ID).spanId(103).build();
    private static final String COMPONENT_NAME = "componentname";
    private static final String OPERATION_NAME = "operationname";

    private ServerClientAndLocalSpanState state;
    private Random mockRandom;
    private Reporter<zipkin.Span> mockReporter;
    private LocalTracer localTracer;

    @Before
    public void setup() {
        mockRandom = mock(Random.class);
        when(mockRandom.nextLong()).thenReturn(555L);

        mockReporter = mock(Reporter.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(START_TIME_MICROSECONDS / 1000);
        PowerMockito.when(System.nanoTime()).thenReturn(0L);

        state = new TestServerClientAndLocalSpanStateCompilation();
        localTracer = LocalTracer.builder()
                .spanAndEndpoint(SpanAndEndpoint.LocalSpanAndEndpoint.create(state))
                .randomGenerator(mockRandom)
                .reporter(mockReporter)
                .allowNestedLocalSpans(false)
                .traceSampler(Sampler.create(1.0f))
                .clock(new AnnotationSubmitter.DefaultClock())
                .traceId128Bit(false)
                .build();
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
        state.setCurrentServerSpan(ServerSpan.create(PARENT_SPAN_ID, "name"));

        PowerMockito.when(System.nanoTime()).thenReturn(500L);

        SpanId expectedSpanId = PARENT_SPAN_ID.toBuilder().spanId(555L).parentId(PARENT_SPAN_ID.spanId).build();

        assertEquals(expectedSpanId, localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME));

        Span started = state.getCurrentLocalSpan();

        assertEquals(START_TIME_MICROSECONDS, started.getTimestamp().longValue());
        assertEquals("lc", started.getBinary_annotations().get(0).getKey());
        assertEquals(COMPONENT_NAME, new String(started.getBinary_annotations().get(0).getValue(), Util.UTF_8));
        assertEquals(state.endpoint(), started.getBinary_annotations().get(0).host);
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
        state.setCurrentServerSpan(ServerSpan.create(PARENT_SPAN_ID, "name"));

        localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME, 1000L);

        Span started = state.getCurrentLocalSpan();
        assertEquals(1000L, started.getTimestamp().longValue());
    }

    @Test
    public void startSpan_unsampled() {
        localTracer = LocalTracer
                .builder(localTracer)
                .traceSampler(Sampler.create(0.0f)).build();

        assertNull(localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME));
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
        Span finished = new Span().setName("foo").setTimestamp(START_TIME_MICROSECONDS);
        state.setCurrentLocalSpan(finished);

        PowerMockito.when(System.nanoTime()).thenReturn(500000L);

        localTracer.finishSpan();

        verify(mockReporter).report(finished.toZipkin());
        verifyNoMoreInteractions(mockReporter);

        assertEquals(500L, finished.getDuration().longValue());
    }

    /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
    @Test
    public void finishSpan_lessThanMicrosRoundUp() {
        Span finished = new Span().setName("foo").setTimestamp(START_TIME_MICROSECONDS);
        state.setCurrentLocalSpan(finished);

        PowerMockito.when(System.nanoTime()).thenReturn(50L);

        localTracer.finishSpan();

        verify(mockReporter).report(finished.toZipkin());
        verifyNoMoreInteractions(mockReporter);

        assertEquals(1L, finished.getDuration().longValue());
    }

    @Test
    public void startSpan_with_inheritable_nested_local_spans() {
        state = new InheritableServerClientAndLocalSpanState(Endpoint.create("test-service", 127 << 24 | 1));
        localTracer = LocalTracer
                .builder(localTracer)
                .spanAndEndpoint(SpanAndEndpoint.LocalSpanAndEndpoint.create(state))
                .allowNestedLocalSpans(true)
                .build();

        assertNull(localTracer.getNewSpanParent());
        state.setCurrentServerSpan(ServerSpan.create(PARENT_SPAN_ID, "name"));

        SpanId span1 = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(PARENT_SPAN_ID.toBuilder().spanId(555L).parentId(PARENT_SPAN_ID.spanId).build(), span1);
        assertEquals(span1.spanId, localTracer.getNewSpanParent().getId());

        SpanId span2 = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(PARENT_SPAN_ID.toBuilder().spanId(555L).parentId(span1.spanId).build(), span2);
        assertEquals(span2.spanId, localTracer.getNewSpanParent().getId());

        assertEquals(span2.spanId, state.getCurrentLocalSpan().getId());
        localTracer.finishSpan();
        assertEquals(span1.spanId, state.getCurrentLocalSpan().getId());
        localTracer.finishSpan();
        assertNull(state.getCurrentLocalSpan());
    }

    @Test
    public void startSpan_nested_local_spans_disabled() {
        state = new InheritableServerClientAndLocalSpanState(Endpoint.create("test-service", 127 << 24 | 1));
        localTracer = LocalTracer
                .builder(localTracer)
                .spanAndEndpoint(SpanAndEndpoint.LocalSpanAndEndpoint.create(state))
                .allowNestedLocalSpans(false)
                .build();

        assertNull(localTracer.getNewSpanParent());
        state.setCurrentServerSpan(ServerSpan.create(PARENT_SPAN_ID, "name"));

        SpanId span1 = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(PARENT_SPAN_ID.toBuilder().spanId(555L).parentId(PARENT_SPAN_ID.spanId).build(), span1);
        assertEquals(state.getCurrentServerSpan().getSpan(), localTracer.getNewSpanParent());

        SpanId span2 = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(PARENT_SPAN_ID.toBuilder().spanId(555L).parentId(PARENT_SPAN_ID.spanId).build(), span2);
        assertEquals(state.getCurrentServerSpan().getSpan(), localTracer.getNewSpanParent());

        assertEquals(span2.spanId, state.getCurrentLocalSpan().getId());
        localTracer.finishSpan();
        assertEquals(span1.spanId, state.getCurrentLocalSpan().getId());
        localTracer.finishSpan();
        assertNull(state.getCurrentLocalSpan());
    }

    @Test
    public void startNewSpan_whenParentHas128bitTraceId() {
        ServerSpan parentSpan = ServerSpan.create(
            PARENT_SPAN_ID.toBuilder().traceIdHigh(3).build(), "name");
        state.setCurrentServerSpan(parentSpan);
        when(mockRandom.nextLong()).thenReturn(1L);

        SpanId newSpanId = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(3, newSpanId.traceIdHigh);
        assertEquals(TRACE_ID, newSpanId.traceId);
    }

    @Test
    public void startNewSpan_rootSpanWith64bitTraceId() {
        when(mockRandom.nextLong()).thenReturn(1L);

        SpanId newSpanId = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(0, newSpanId.traceIdHigh);
        assertEquals(1, newSpanId.traceId);
    }

    @Test
    public void startNewSpan_rootSpanWith128bitTraceId() {
        localTracer = new AutoValue_LocalTracer.Builder(localTracer)
            .traceId128Bit(true).build();
        when(mockRandom.nextLong()).thenReturn(1L, 3L);

        SpanId newSpanId = localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertEquals(3, newSpanId.traceIdHigh);
        assertEquals(1, newSpanId.traceId);
    }
}
