package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.twitter.zipkin.gen.zipkinCoreConstants.LOCAL_COMPONENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest(LocalTracer.class)
public class LocalTracerTest {

    private final static String COMPONENT_NAME = "componentname";
    private final static String OPERATION_NAME = "operationname";

    private ServerAndClientSpanState mockState;
    private ClientTracer mockClientTracer;
    private SpanCollector mockSpanCollector;
    private Endpoint endpoint;

    private LocalTracer localTracer;

    @Before
    public void setup() {
        mockState = mock(ServerAndClientSpanState.class);
        endpoint = new Endpoint();
        when(mockState.getServerEndpoint()).thenReturn(endpoint);

        PowerMockito.mockStatic(System.class);
        mockClientTracer = mock(ClientTracer.class);
        mockSpanCollector = mock(SpanCollector.class);

        localTracer = LocalTracer.create(mockClientTracer, mockSpanCollector, mockState);
    }

    /**
     * When a local span is started without a timestamp, microseconds and a tick are recorded for
     * duration calculation. A binary annotation is added for search by component.
     * <p>
     * <p/>Ex.
     * <pre>
     * localTracer.startSpan(component, operation); // internally time and nanos are recorded
     * </pre>
     */
    @Test
    public void startSpan() {
        SpanId spanId = mock(SpanId.class);
        Span started = new Span();
        when(mockClientTracer.startNewSpan(OPERATION_NAME)).thenReturn(spanId);
        when(mockState.getCurrentClientSpan()).thenReturn(started);
        when(mockClientTracer.currentTimeMicroseconds()).thenReturn(1000L);
        PowerMockito.when(System.nanoTime()).thenReturn(500L);

        assertEquals(spanId, localTracer.startSpan(COMPONENT_NAME, OPERATION_NAME));
        assertEquals(1000L, started.timestamp);
        assertEquals(500L, started.startTick.longValue());

        verify(mockClientTracer).startNewSpan(OPERATION_NAME);
        verify(mockClientTracer).submitBinaryAnnotation(LOCAL_COMPONENT, COMPONENT_NAME); // for lookup by service
        verify(mockState).getCurrentClientSpan();
        verify(mockClientTracer).currentTimeMicroseconds();
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);
    }

    /**
     * When a span is started with a timestamp, we can't use nanotime for duration as we don't
     * know the nanotime value for that timestamp.
     * <p>
     * <p/>Ex.
     * <pre>
     * localTracer.startSpan(component, operation, startTime);
     *
     * </pre>
     */
    @Test
    public void startSpan_userSuppliedTimestamp() {
        SpanId spanId = mock(SpanId.class);
        Span started = new Span();
        when(mockClientTracer.startNewSpan(OPERATION_NAME)).thenReturn(spanId);
        when(mockState.getCurrentClientSpan()).thenReturn(started);

        assertEquals(spanId, localTracer.startSpan(COMPONENT_NAME, OPERATION_NAME, 1000L));
        assertEquals(1000L, started.timestamp);
        assertNull(started.startTick);

        verify(mockClientTracer).startNewSpan(OPERATION_NAME);
        verify(mockClientTracer).submitBinaryAnnotation(LOCAL_COMPONENT, COMPONENT_NAME); // for lookup by service
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);
    }

    @Test
    public void startSpan_unsampled() {
        when(mockClientTracer.startNewSpan(OPERATION_NAME)).thenReturn(null);
        assertNull(localTracer.startSpan(COMPONENT_NAME, OPERATION_NAME));

        verify(mockClientTracer).startNewSpan(OPERATION_NAME);
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);
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

        PowerMockito.when(System.nanoTime()).thenReturn(1000000L);
        when(mockState.getCurrentClientSpan()).thenReturn(finished);

        localTracer.finishSpan();

        verify(mockState, times(2)).getCurrentClientSpan(); // 2 times, since Span.duration is derived
        verify(mockSpanCollector).collect(finished);
        verify(mockState).setCurrentClientServiceName(null);
        verify(mockState).setCurrentClientSpan(null);
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);

        assertEquals(500L, finished.duration);
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

        when(mockState.getCurrentClientSpan()).thenReturn(finished);
        when(mockClientTracer.currentTimeMicroseconds()).thenReturn(1500L);

        localTracer.finishSpan();

        verify(mockState, times(2)).getCurrentClientSpan(); // 2 times, since Span.duration is derived
        verify(mockClientTracer).currentTimeMicroseconds();
        verify(mockSpanCollector).collect(finished);
        verify(mockState).setCurrentClientServiceName(null);
        verify(mockState).setCurrentClientSpan(null);
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);

        assertEquals(500L, finished.duration);
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

        when(mockState.getCurrentClientSpan()).thenReturn(finished);

        localTracer.finishSpan(500L);

        verify(mockState).getCurrentClientSpan();
        verify(mockSpanCollector).collect(finished);
        verify(mockState).setCurrentClientServiceName(null);
        verify(mockState).setCurrentClientSpan(null);
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);

        assertEquals(500L, finished.duration);
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

        when(mockState.getCurrentClientSpan()).thenReturn(finished);

        localTracer.finishSpan(500L);

        verify(mockState).getCurrentClientSpan();
        verify(mockSpanCollector).collect(finished);
        verify(mockState).setCurrentClientServiceName(null);
        verify(mockState).setCurrentClientSpan(null);
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);

        assertEquals(500L, finished.duration);
    }

    @Test
    public void finishSpan_unsampled() {
        when(mockState.getCurrentClientSpan()).thenReturn(null);
        localTracer.finishSpan();
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);
    }

    @Test
    public void finishSpan_unsampled_userSuppliedDuration() {
        when(mockState.getCurrentClientSpan()).thenReturn(null);
        localTracer.finishSpan(5000L);
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockClientTracer, mockState, mockClientTracer);
    }
}
