package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

public class ServerTracerImplTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private final static String ANNOTATION_NAME = "Annotation name";
    private final static long TRACE_ID = 1l;
    private final static long SPAN_ID = 2l;
    private final static Long PARENT_SPANID = 3l;
    private final static Boolean SAMPLE = true;
    private final static String SPAN_NAME = "span name";
    private static final String KEY = "key";
    private static final String VALUE = "stringValue";
    private static final int INT_VALUE = 14;
    private static final long END_DATE = 10;
    private static final long START_DATE = 10000;
    private static final long DURATION_MS = 13;

    private ServerTracerImpl serverTracer;
    private ServerSpanState mockServerSpanState;
    private SpanCollector mockSpanCollector;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndPoint;
    private CommonAnnotationSubmitter mockAnnotationSubmitter;

    @Before
    public void setup() {

        mockServerSpanState = mock(ServerSpanState.class);
        mockSpanCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);
        mockServerSpan = mock(ServerSpan.class);

        mockEndPoint = new Endpoint();
        mockAnnotationSubmitter = mock(CommonAnnotationSubmitter.class);

        serverTracer = new ServerTracerImpl(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter) {

            @Override
            long currentTimeMicroseconds() {
                return CURRENT_TIME_MICROSECONDS;
            }
        };

    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new ServerTracerImpl(null, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullCollector() {
        new ServerTracerImpl(mockServerSpanState, null, mockAnnotationSubmitter);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullAnnotationSubmitter() {
        new ServerTracerImpl(mockServerSpanState, mockSpanCollector, null);
    }

    @Test
    public void testClearCurrentSpan() {
        serverTracer.clearCurrentSpan();
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetSpan() {

        serverTracer.setSpan(TRACE_ID, SPAN_ID, PARENT_SPANID, SPAN_NAME);

        final ServerSpan expectedServerSpan = new ServerSpanImpl(TRACE_ID, SPAN_ID, PARENT_SPANID, SPAN_NAME);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetNoSampling() {
        serverTracer.setNoSampling();
        final ServerSpan expectedServerSpan = new ServerSpanImpl(false);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationWithStartEndDateNoServerSpan() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpan.getSpan()).thenReturn(null);
        serverTracer.submitAnnotation(ANNOTATION_NAME, START_DATE, END_DATE);
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationWithStartEndDateNoEndPoint() {

        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        serverTracer.submitAnnotation(ANNOTATION_NAME, START_DATE, END_DATE);
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();
        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, null, ANNOTATION_NAME, START_DATE, END_DATE);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationStringNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpan.getSpan()).thenReturn(null);
        serverTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationString() {
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, mockEndPoint, ANNOTATION_NAME);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitBinaryAnnotationString() {
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.submitBinaryAnnotation(KEY, VALUE);
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitBinaryAnnotation(mockSpan, mockEndPoint, KEY, VALUE);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitBinaryAnnotationInt() {
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.submitBinaryAnnotation(KEY, INT_VALUE);
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitBinaryAnnotation(mockSpan, mockEndPoint, KEY, INT_VALUE);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerReceivedNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpan.getSpan()).thenReturn(null);
        serverTracer.setServerReceived();
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerReceived() {
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerReceived();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, mockEndPoint, zipkinCoreConstants.SERVER_RECV);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerSendShouldNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpan.getSpan()).thenReturn(null);
        serverTracer.setServerSend();
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerSend() {
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerSend();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, mockEndPoint, zipkinCoreConstants.SERVER_SEND);
        verify(mockServerSpanState).getServerSpanThreadDuration();
        verify(mockSpanCollector).collect(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerSendInCaseOfThreadDuration() {
        when(mockServerSpan.getSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockServerSpanState.getServerSpanThreadDuration()).thenReturn(DURATION_MS);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerSend();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState, times(2)).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, mockEndPoint, zipkinCoreConstants.SERVER_SEND);
        verify(mockAnnotationSubmitter).submitBinaryAnnotation(mockSpan, mockEndPoint, BraveAnnotations.THREAD_DURATION,
            String.valueOf(DURATION_MS));
        verify(mockServerSpanState).getServerSpanThreadDuration();
        verify(mockSpanCollector).collect(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testGetThreadDuration() {
        when(mockServerSpanState.getServerSpanThreadDuration()).thenReturn(DURATION_MS);
        assertEquals(DURATION_MS, serverTracer.getThreadDuration());

        verify(mockServerSpanState).getServerSpanThreadDuration();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

}
