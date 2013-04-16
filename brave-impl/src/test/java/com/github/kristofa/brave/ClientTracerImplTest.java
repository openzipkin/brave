package com.github.kristofa.brave;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Random;

import org.junit.Before;
import org.junit.Test;

public class ClientTracerImplTest {

    private final static String REQUEST_NAME = "requestName";
    private final static String ANNOTATION_NAME = "annotationName";
    private final static int DURATION = 11;

    private ServerAndClientSpanState mockState;
    private Random mockRandom;
    private SpanCollector mockCollector;
    private ClientTracerImpl clientTracer;
    private Span mockSpan;
    private EndPoint mockEndPoint;

    @Before
    public void setup() {
        mockState = mock(ServerAndClientSpanState.class);
        mockEndPoint = mock(EndPoint.class);
        when(mockState.shouldTrace()).thenReturn(true);
        when(mockState.getEndPoint()).thenReturn(mockEndPoint);

        mockRandom = mock(Random.class);
        mockCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);

        clientTracer = new ClientTracerImpl(mockState, mockRandom, mockCollector);

    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new ClientTracerImpl(null, mockRandom, mockCollector);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullRandom() {
        new ClientTracerImpl(mockState, null, mockCollector);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullCollector() {
        new ClientTracerImpl(mockState, mockRandom, null);
    }

    @Test
    public void testSetClientSentShouldTraceFalse() {
        when(mockState.shouldTrace()).thenReturn(false);
        clientTracer.setClientSent();
        verify(mockState).shouldTrace();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testSetClientSent() {

        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.setClientSent();

        final AnnotationImpl expectedAnnotation = new AnnotationImpl("cs", mockEndPoint);
        verify(mockState).shouldTrace();
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();
        verify(mockSpan).addAnnotation(expectedAnnotation);
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testSetClientReceivedShouldTraceFalse() {
        when(mockState.shouldTrace()).thenReturn(false);
        clientTracer.setClientReceived();
        verify(mockState).shouldTrace();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testSetClientReceived() {

        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.setClientReceived();

        final AnnotationImpl expectedAnnotation = new AnnotationImpl("cr", mockEndPoint);
        verify(mockState).shouldTrace();
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();
        verify(mockSpan).addAnnotation(expectedAnnotation);
        verify(mockState).setCurrentClientSpan(null);
        verify(mockCollector).collect(mockSpan);
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testStartNewSpanShouldTraceFalse() {
        when(mockState.shouldTrace()).thenReturn(false);
        assertNotNull(clientTracer.startNewSpan(REQUEST_NAME));
        verify(mockState).shouldTrace();
        verify(mockState).getCurrentServerSpan();
        verify(mockRandom, times(2)).nextLong();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testStartNewSpanNotPartOfExistingSpan() {

        when(mockState.getCurrentServerSpan()).thenReturn(null);
        when(mockRandom.nextLong()).thenReturn(1l).thenReturn(2l);

        clientTracer.startNewSpan(REQUEST_NAME);

        final SpanIdImpl expectedSpanId = new SpanIdImpl(2, 1, null);
        final Span expectedSpan = new SpanImpl(expectedSpanId, REQUEST_NAME);

        verify(mockState).shouldTrace();
        verify(mockRandom, times(2)).nextLong();
        verify(mockState).getCurrentServerSpan();
        verify(mockState).setCurrentClientSpan(expectedSpan);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testSubmitAnnotationStringLongShouldTraceFalse() {
        when(mockState.shouldTrace()).thenReturn(false);
        clientTracer.submitAnnotation(ANNOTATION_NAME, DURATION);
        verify(mockState).shouldTrace();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testSubmitAnnotationStringLong() {

        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.submitAnnotation(ANNOTATION_NAME, DURATION);
        verify(mockState).shouldTrace();
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();

        final Annotation expectedAnnotation = new AnnotationImpl(ANNOTATION_NAME, mockEndPoint, DURATION);
        verify(mockSpan).addAnnotation(expectedAnnotation);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testSubmitAnnotationStringShouldTraceFalse() {
        when(mockState.shouldTrace()).thenReturn(false);
        clientTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockState).shouldTrace();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testSubmitAnnotationString() {
        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockState).shouldTrace();
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();

        final Annotation expectedAnnotation = new AnnotationImpl(ANNOTATION_NAME, mockEndPoint);
        verify(mockSpan).addAnnotation(expectedAnnotation);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);

    }

}
