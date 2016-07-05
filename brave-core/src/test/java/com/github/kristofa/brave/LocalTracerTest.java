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

import com.twitter.zipkin.gen.Span;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AnnotationSubmitter.class, LocalTracer.class})
public class LocalTracerTest {

    private static final long PARENT_TRACE_ID = 105;
    private static final long PARENT_SPAN_ID = 103;
    private static final String COMPONENT_NAME = "componentname";
    private static final String OPERATION_NAME = "operationname";

    private ServerClientAndLocalSpanState state = new TestServerClientAndLocalSpanStateCompilation();
    private Random mockRandom;
    private SpanCollector mockCollector;
    private LocalTracer localTracer;

    @Before
    public void setup() {
        mockRandom = mock(Random.class);
        when(mockRandom.nextLong()).thenReturn(555L);

        mockCollector = mock(SpanCollector.class);

        PowerMockito.mockStatic(System.class);
        localTracer = LocalTracer.builder()
                .spanAndEndpoint(SpanAndEndpoint.LocalSpanAndEndpoint.create(state))
                .randomGenerator(mockRandom)
                .spanCollector(mockCollector)
                .traceSampler(Sampler.create(1.0f))
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
        state.setCurrentServerSpan(ServerSpan.create(PARENT_TRACE_ID, PARENT_SPAN_ID, null, "name"));

        PowerMockito.when(System.currentTimeMillis()).thenReturn(1L);
        PowerMockito.when(System.nanoTime()).thenReturn(500L);

        SpanId expectedSpanId =
            SpanId.builder().traceId(PARENT_TRACE_ID).spanId(555L).parentId(PARENT_SPAN_ID).build();

        assertEquals(expectedSpanId, localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME));

        Span started = state.getCurrentLocalSpan();

        assertEquals(1000L, started.getTimestamp().longValue());
        assertEquals(500L, started.startTick.longValue());
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
        state.setCurrentServerSpan(ServerSpan.create(PARENT_TRACE_ID, PARENT_SPAN_ID, null, "name"));

        localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME, 1000L);

        Span started = state.getCurrentLocalSpan();
        assertEquals(1000L, started.getTimestamp().longValue());
        assertNull(started.startTick);
    }

    @Test
    public void startSpan_unsampled() {
        localTracer = LocalTracer
                .builder(localTracer)
                .traceSampler(Sampler.create(0.0f)).build();

        assertNull(localTracer.startNewSpan(COMPONENT_NAME, OPERATION_NAME));
    }

    /**
     * When finish is called without a duration, the startTick from start is used in duration calculation.
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
        Span finished = new Span().setTimestamp(1000L); // set in start span
        finished.startTick = 500000L; // set in start span
        state.setCurrentLocalSpan(finished);

        PowerMockito.when(System.nanoTime()).thenReturn(1000000L);

        localTracer.finishSpan();

        verify(mockCollector).collect(finished);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(500L, finished.getDuration().longValue());
    }

    /**
     * When a span is started with a timestamp, nanos aren't known, so duration calculation falls back to system time.
     * <p>
     * <p/>Ex.
     * <pre>
     * localTracer.startSpan(component, operation, startTime); // no tick was recorded
     * ...
     * localTracer.finishSpan(); // can't know which nanos startTime was associated with!
     * </pre>
     */
    @Test
    public void finishSpan_userSuppliedTimestamp() {
        Span finished = new Span().setTimestamp(1000L); // Set by user
        state.setCurrentLocalSpan(finished);

        PowerMockito.when(System.currentTimeMillis()).thenReturn(2L);

        localTracer.finishSpan();

        verify(mockCollector).collect(finished);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(1000L, finished.getDuration().longValue());
    }

    /**
     * When a local span completes with a user supplied duration, startTick is ignored.
     * <p>
     * <p/>Ex.
     * <pre>
     * localTracer.startSpan(component, operation); // startTick was recorded, but ignored
     * ...
     * localTracer.finishSpan(duration); // user calculated duration out-of-band, ex with a stop watch.
     * </pre>
     */
    @Test
    public void finishSpan_userSuppliedDuration() {
        Span finished = new Span().setTimestamp(1000L); // set in start span
        finished.startTick = 500L; // set in start span
        state.setCurrentLocalSpan(finished);

        localTracer.finishSpan(500L);

        verify(mockCollector).collect(finished);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(500L, finished.getDuration().longValue());
    }

    /**
     * When a span starts and finishes with user-supplied timestamp and duration, nanotime is used
     * <p>
     * <p/>Ex.
     * <pre>
     * localTracer.startSpan(component, operation, startTime); // startTick was recorded
     * ...
     * localTracer.finishSpan(duration); // nanoTime - startTick = duration
     * </pre>
     */
    @Test
    public void finishSpan_userSuppliedTimestampAndDuration() {
        Span finished = new Span().setTimestamp(1000L); // Set by user
        state.setCurrentLocalSpan(finished);

        localTracer.finishSpan(500L);

        verify(mockCollector).collect(finished);
        verifyNoMoreInteractions(mockCollector);

        assertEquals(500L, finished.getDuration().longValue());
    }

    @Test
    public void finishSpan_unsampled() {
        state.setCurrentLocalSpan(null);

        localTracer.finishSpan();

        verifyNoMoreInteractions(mockCollector);
    }

    @Test
    public void finishSpan_unsampled_userSuppliedDuration() {
        state.setCurrentLocalSpan(null);

        localTracer.finishSpan(5000L);

        verifyNoMoreInteractions(mockCollector);
    }
}
