package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

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
    private static final Endpoint ENDPOINT = Endpoint.create("serviceName", 80);
    private static final zipkin.Endpoint ZIPKIN_ENDPOINT = zipkin.Endpoint.create("serviceName", 80);

    private Span span = Brave.newSpan(SpanId.builder().spanId(TRACE_ID).build());

    List<zipkin.Span> spans = new ArrayList<>();
    Brave brave;
    Recorder recorder;

    @Before
    public void setup() {
        ThreadLocalServerClientAndLocalSpanState.clear();

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(START_TIME_MICROSECONDS / 1000);
        PowerMockito.when(System.nanoTime()).thenReturn(0L);

        // deferred so that mockito can work on the clock!
        brave = new Brave.Builder(ENDPOINT)
            .clock(new AnnotationSubmitter.DefaultClock())
            .reporter(spans::add).build();
        recorder = brave.localTracer().recorder();
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
        brave.serverTracer().setStateCurrentTrace(PARENT_CONTEXT, "name");

        PowerMockito.when(System.nanoTime()).thenReturn(500L);

        SpanId newContext = brave.localTracer().startNewSpan(COMPONENT_NAME, OPERATION_NAME);
        assertThat(newContext).isEqualTo(
            PARENT_CONTEXT.toBuilder()
                .parentId(PARENT_CONTEXT.spanId)
                .spanId(newContext.spanId)
                .build()
        );

        assertThat(spans).isEmpty(); // doesn't flush on start
        recorder.flush(brave.localSpanThreadBinder().get());

        zipkin.Span started = spans.get(0);
        assertThat(started.timestamp).isEqualTo(START_TIME_MICROSECONDS);
        assertThat(started.name).isEqualTo(OPERATION_NAME);
        assertThat(started.binaryAnnotations).containsExactly(
            zipkin.BinaryAnnotation.create("lc", COMPONENT_NAME, ZIPKIN_ENDPOINT)
        );
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
        brave.serverTracer().setStateCurrentTrace(PARENT_CONTEXT, "name");

        brave.localTracer().startNewSpan(COMPONENT_NAME, OPERATION_NAME, 1000L);

        recorder.flush(brave.localSpanThreadBinder().get());
        assertThat(spans.get(0).timestamp).isEqualTo(1000L);
    }

    @Test
    public void startSpan_unsampled() {
        brave = new Brave.Builder().traceSampler(Sampler.NEVER_SAMPLE).build();

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
        recorder.start(span, START_TIME_MICROSECONDS);
        brave.localSpanThreadBinder().setCurrentSpan(span);

        PowerMockito.when(System.nanoTime()).thenReturn(500000L);

        brave.localTracer().finishSpan();

        assertThat(spans.get(0).duration).isEqualTo(500L);
    }

    /** Duration of less than one microsecond is confusing to plot and could coerce to null. */
    @Test
    public void finishSpan_lessThanMicrosRoundUp() {
        recorder.start(span, START_TIME_MICROSECONDS);
        brave.localSpanThreadBinder().setCurrentSpan(span);

        PowerMockito.when(System.nanoTime()).thenReturn(50L);

        brave.localTracer().finishSpan();

        assertThat(spans.get(0).duration).isEqualTo(1L);
    }

    @Test
    public void startSpan_with_inheritable_nested_local_spans() {
        InheritableServerClientAndLocalSpanState state =
            new InheritableServerClientAndLocalSpanState(Endpoint.create("test-service", 127 << 24 | 1));
        brave = new Brave.Builder(state).reporter(spans::add).build();
        recorder = brave.localTracer().recorder();
        LocalTracer localTracer = brave.localTracer();

        assertNull(localTracer.maybeParent());
        brave.serverTracer().setStateCurrentTrace(PARENT_CONTEXT, "name");

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
}
