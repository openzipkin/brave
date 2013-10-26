package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

public class ServerTracerImplTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private final static long TRACE_ID = 1l;
    private final static long SPAN_ID = 2l;
    private final static Long PARENT_SPANID = 3l;
    private final static String SPAN_NAME = "span name";
    private static final long DURATION_MS = 13;

    private ServerTracerImpl serverTracer;
    private ServerSpanState mockServerSpanState;
    private SpanCollector mockSpanCollector;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndPoint;
    private Random mockRandom;
    private TraceFilter mockTraceFilter1;
    private TraceFilter mockTraceFilter2;
    private List<TraceFilter> traceFilters;

    @Before
    public void setup() {

        mockServerSpanState = mock(ServerSpanState.class);
        mockSpanCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);
        mockServerSpan = mock(ServerSpan.class);

        mockEndPoint = new Endpoint();
        mockRandom = mock(Random.class);
        mockTraceFilter1 = mock(TraceFilter.class);
        mockTraceFilter2 = mock(TraceFilter.class);

        traceFilters = Arrays.asList(mockTraceFilter1, mockTraceFilter2);

        serverTracer = new ServerTracerImpl(mockServerSpanState, mockRandom,  mockSpanCollector, traceFilters) {

            @Override
            long currentTimeMicroseconds() {
                return CURRENT_TIME_MICROSECONDS;
            }
        };

    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new ServerTracerImpl(null, mockRandom, mockSpanCollector, traceFilters);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullCollector() {
        new ServerTracerImpl(mockServerSpanState, mockRandom, null, traceFilters);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullTraceFilters() {
        new ServerTracerImpl(mockServerSpanState, mockRandom, mockSpanCollector, null);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullRandom() {
        new ServerTracerImpl(mockServerSpanState, null, mockSpanCollector, traceFilters);
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
        final ServerSpanImpl expectedServerSpan = new ServerSpanImpl(TRACE_ID, SPAN_ID, PARENT_SPANID, SPAN_NAME);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateNoTracing() {
        serverTracer.setStateNoTracing();
        final ServerSpanImpl expectedServerSpan = new ServerSpanImpl(false);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateUnknownTraceFiltersTrue() {

        when(mockTraceFilter1.trace(SPAN_NAME)).thenReturn(true);
        when(mockTraceFilter2.trace(SPAN_NAME)).thenReturn(true);
        when(mockRandom.nextLong()).thenReturn(TRACE_ID);

        serverTracer.setStateUnknown(SPAN_NAME);
        final ServerSpanImpl expectedServerSpan = new ServerSpanImpl(TRACE_ID, TRACE_ID, null, SPAN_NAME);

        final InOrder inOrder = inOrder(mockTraceFilter1, mockTraceFilter2, mockRandom, mockServerSpanState);

        inOrder.verify(mockTraceFilter1).trace(SPAN_NAME);
        inOrder.verify(mockTraceFilter2).trace(SPAN_NAME);
        inOrder.verify(mockRandom).nextLong();
        inOrder.verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockRandom);
    }

    @Test
    public void testSetStateUnknownTraceFiltersFalse() {

        when(mockTraceFilter1.trace(SPAN_NAME)).thenReturn(true);
        when(mockTraceFilter2.trace(SPAN_NAME)).thenReturn(false);

        final ServerSpanImpl expectedServerSpan = new ServerSpanImpl(false);

        serverTracer.setStateUnknown(SPAN_NAME);

        final InOrder inOrder = inOrder(mockTraceFilter1, mockTraceFilter2, mockRandom, mockServerSpanState);

        inOrder.verify(mockTraceFilter1).trace(SPAN_NAME);
        inOrder.verify(mockTraceFilter2).trace(SPAN_NAME);
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
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerReceived();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(mockEndPoint);
        expectedAnnotation.setValue(zipkinCoreConstants.SERVER_RECV);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();
        verify(mockSpan).addToAnnotations(expectedAnnotation);

        verifyNoMoreInteractions(mockServerSpanState, mockSpan, mockSpanCollector);
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
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerSend();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(mockEndPoint);
        expectedAnnotation.setValue(zipkinCoreConstants.SERVER_SEND);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        verify(mockServerSpanState, times(2)).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verify(mockServerSpanState).getServerSpanThreadDuration();
        verify(mockSpanCollector).collect(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockSpan);
    }

    @Test
    public void testSetServerSendInCaseOfThreadDuration() throws UnsupportedEncodingException {
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getServerSpanThreadDuration()).thenReturn(DURATION_MS);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerSend();
        verify(mockServerSpanState, times(3)).getCurrentServerSpan();
        verify(mockServerSpanState, times(2)).getEndPoint();

        final Annotation expectedServerSendAnnotation = new Annotation();
        expectedServerSendAnnotation.setHost(mockEndPoint);
        expectedServerSendAnnotation.setValue(zipkinCoreConstants.SERVER_SEND);
        expectedServerSendAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        final BinaryAnnotation expectedThreadDurationAnnotation = new BinaryAnnotation();
        expectedThreadDurationAnnotation.setAnnotation_type(AnnotationType.STRING);
        expectedThreadDurationAnnotation.setHost(mockEndPoint);
        expectedThreadDurationAnnotation.setKey(BraveAnnotations.THREAD_DURATION);
        final ByteBuffer bb = ByteBuffer.wrap(String.valueOf(DURATION_MS).getBytes("UTF-8"));
        expectedThreadDurationAnnotation.setValue(bb);

        verify(mockSpan).addToAnnotations(expectedServerSendAnnotation);
        verify(mockSpan).addToBinary_annotations(expectedThreadDurationAnnotation);

        verify(mockServerSpanState).getServerSpanThreadDuration();
        verify(mockSpanCollector).collect(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockSpan);
    }

    @Test
    public void testGetThreadDuration() {
        when(mockServerSpanState.getServerSpanThreadDuration()).thenReturn(DURATION_MS);
        assertEquals(DURATION_MS, serverTracer.getThreadDuration());

        verify(mockServerSpanState).getServerSpanThreadDuration();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

}
