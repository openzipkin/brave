package com.github.kristofa.brave;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class ServerTracerImplTest {

    private final static String ANNOTATION_NAME = "Annotation name";
    private final static long DURATION = 13;

    private ServerTracerImpl serverTracer;
    private ServerSpanState mockServerSpanState;
    private SpanCollector mockSpanCollector;
    private Span mockSpan;
    private EndPoint mockEndPoint;

    @Before
    public void setup() {

        mockServerSpanState = mock(ServerSpanState.class);
        mockSpanCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);
        mockEndPoint = mock(EndPoint.class);
        serverTracer = new ServerTracerImpl(mockServerSpanState, mockSpanCollector);
        when(mockServerSpanState.shouldTrace()).thenReturn(true);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new ServerTracerImpl(null, mockSpanCollector);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullCollector() {
        new ServerTracerImpl(mockServerSpanState, null);
    }

    @Test
    public void testSetSpan() {
        final SpanIdImpl spanIdImpl = new SpanIdImpl(1l, 2l, 3l);
        final String name = "spanName";

        serverTracer.setSpan(spanIdImpl, name);

        final Span expectedSpan = new SpanImpl(spanIdImpl, name);
        verify(mockServerSpanState).setCurrentServerSpan(expectedSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetShouldTrace() {

        serverTracer.setShouldTrace(false);
        verify(mockServerSpanState).setTracing(false);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSubmitAnnotationStringLongShouldNotTrace() {
        when(mockServerSpanState.shouldTrace()).thenReturn(false);
        serverTracer.submitAnnotation(ANNOTATION_NAME, DURATION);
        verify(mockServerSpanState).shouldTrace();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSubmitAnnotationStringLongNoServerSpan() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(null);
        serverTracer.submitAnnotation(ANNOTATION_NAME, DURATION);
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSubmitAnnotationStringLongNoEndPoint() {

        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        serverTracer.submitAnnotation(ANNOTATION_NAME, DURATION);
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSubmitAnnotationStringShouldNotTrace() {

        when(mockServerSpanState.shouldTrace()).thenReturn(false);
        serverTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockServerSpanState).shouldTrace();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSubmitAnnotationString() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        final Annotation expectedAnnotation = new AnnotationImpl(ANNOTATION_NAME, mockEndPoint, null);
        verify(mockSpan).addAnnotation(expectedAnnotation);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetServerReceivedShouldNotTrace() {

        when(mockServerSpanState.shouldTrace()).thenReturn(false);
        serverTracer.setServerReceived();
        verify(mockServerSpanState).shouldTrace();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetServerReceived() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerReceived();
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        final Annotation expectedAnnotation = new AnnotationImpl("sr", mockEndPoint, null);
        verify(mockSpan).addAnnotation(expectedAnnotation);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetServerSendShouldNotTrace() {

        when(mockServerSpanState.shouldTrace()).thenReturn(false);
        serverTracer.setServerSend();
        verify(mockServerSpanState).shouldTrace();
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetServerSend() {
        when(mockServerSpanState.getCurrentServerSpan()).thenReturn(mockSpan);
        when(mockServerSpanState.getEndPoint()).thenReturn(mockEndPoint);
        serverTracer.setServerSend();
        verify(mockServerSpanState).shouldTrace();
        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getEndPoint();

        final Annotation expectedAnnotation = new AnnotationImpl("ss", mockEndPoint, null);
        verify(mockSpan).addAnnotation(expectedAnnotation);
        verify(mockSpanCollector).collect(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

}
