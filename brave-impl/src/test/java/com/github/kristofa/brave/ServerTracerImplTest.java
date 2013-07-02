package com.github.kristofa.brave;

import static org.mockito.Mockito.mock;
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
    private final static int DURATION = 13;
    private final static long TRACE_ID = 1l;
    private final static long SPAN_ID = 2l;
    private final static Long PARENT_SPANID = 3l;
    private final static String SPAN_NAME = "span name";
    private static final String KEY = "key";
    private static final String VALUE = "stringValue";
    private static final int INT_VALUE = 14;
    private static final long END_DATE = 10;
    private static final long START_DATE = 10000;

    private ServerTracerImpl serverTracer;
    private ServerSpanState mockServerSpanState;
    private SpanCollector mockSpanCollector;
    private Span mockSpan;
    private Endpoint mockEndPoint;
    private CommonAnnotationSubmitter mockAnnotationSubmitter;

    @Before
    public void setup() {

        mockServerSpanState = mock(ServerSpanState.class);
        mockSpanCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);
        mockEndPoint = new Endpoint();
        mockAnnotationSubmitter = mock(CommonAnnotationSubmitter.class);

        serverTracer = new ServerTracerImpl(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter) {

            @Override
            long currentTimeMicroseconds() {
                return CURRENT_TIME_MICROSECONDS;
            }
        };

        when(mockServerSpanState.shouldTrace()).thenReturn(true);
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

        final Span expectedSpan = new Span();
        expectedSpan.setTrace_id(TRACE_ID);
        expectedSpan.setId(SPAN_ID);
        expectedSpan.setParent_id(PARENT_SPANID);
        expectedSpan.setName(SPAN_NAME);
        verify(mockServerSpanState).setCurrentServerSpan(expectedSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetShouldTrace() {

        serverTracer.setShouldTrace(false);
        verify(mockServerSpanState).setTracing(false);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationWithDurationShouldNotTrace() {
        when(mockServerSpanState.shouldTrace()).thenReturn(false);
        serverTracer.submitAnnotation(ANNOTATION_NAME, START_DATE, END_DATE);
        verify(mockServerSpanState).shouldTrace();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationWithDurationNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(null);
        serverTracer.submitAnnotation(ANNOTATION_NAME, START_DATE, END_DATE);
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationWithDurationNoEndPoint() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        serverTracer.submitAnnotation(ANNOTATION_NAME, START_DATE, END_DATE);
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();
        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, null, ANNOTATION_NAME, START_DATE, END_DATE);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationStringShouldNotTrace() {

        when(mockServerSpanState.shouldTrace()).thenReturn(false);
        serverTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockServerSpanState).shouldTrace();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationString() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, mockEndPoint, ANNOTATION_NAME);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitBinaryAnnotationString() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.submitBinaryAnnotation(KEY, VALUE);
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitBinaryAnnotation(mockSpan, mockEndPoint, KEY, VALUE);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitBinaryAnnotationInt() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.submitBinaryAnnotation(KEY, INT_VALUE);
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitBinaryAnnotation(mockSpan, mockEndPoint, KEY, INT_VALUE);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerReceivedShouldNotTrace() {

        when(mockServerSpanState.shouldTrace()).thenReturn(false);
        serverTracer.setServerReceived();
        verify(mockServerSpanState).shouldTrace();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerReceived() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerReceived();
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, mockEndPoint, zipkinCoreConstants.SERVER_RECV);

        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerSendShouldNotTrace() {

        when(mockServerSpanState.shouldTrace()).thenReturn(false);
        serverTracer.setServerSend();
        verify(mockServerSpanState).shouldTrace();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSetServerSend() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerSend();
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, mockEndPoint, zipkinCoreConstants.SERVER_SEND);
        verify(mockSpanCollector).collect(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockAnnotationSubmitter);
    }

}
