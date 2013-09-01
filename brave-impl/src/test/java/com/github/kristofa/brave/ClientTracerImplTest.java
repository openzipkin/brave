package com.github.kristofa.brave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

public class ClientTracerImplTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private final static String REQUEST_NAME = "requestName";
    private final static String ANNOTATION_NAME = "annotationName";
    private final static long START_DATE = 11;
    private final static long END_DATE = 11000;
    private static final String KEY = "key";
    private static final String STRING_VALUE = "stringValue";
    private static final int INT_VALUE = 21;
    private static final long PARENT_SPAN_ID = 103;
    private static final long PARENT_TRACE_ID = 105;

    private ServerAndClientSpanState mockState;
    private Random mockRandom;
    private SpanCollector mockCollector;
    private ClientTracerImpl clientTracer;
    private Span mockSpan;
    private Endpoint endPoint;
    private TraceFilter mockTraceFilter;
    private TraceFilter mockTraceFilter2;
    private CommonAnnotationSubmitter mockAnnotationSubmitter;

    @Before
    public void setup() {
        mockState = mock(ServerAndClientSpanState.class);
        endPoint = new Endpoint();
        mockTraceFilter = mock(TraceFilter.class);
        mockTraceFilter2 = mock(TraceFilter.class);
        when(mockState.getEndPoint()).thenReturn(endPoint);
        when(mockTraceFilter.trace(REQUEST_NAME)).thenReturn(true);
        when(mockTraceFilter2.trace(REQUEST_NAME)).thenReturn(true);

        mockRandom = mock(Random.class);
        mockCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);
        mockAnnotationSubmitter = mock(CommonAnnotationSubmitter.class);

        clientTracer =
            new ClientTracerImpl(mockState, mockRandom, mockCollector, Arrays.asList(mockTraceFilter, mockTraceFilter2),
                mockAnnotationSubmitter) {

                @Override
                long currentTimeMicroseconds() {
                    return CURRENT_TIME_MICROSECONDS;
                }
            };

    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullState() {
        new ClientTracerImpl(null, mockRandom, mockCollector, Arrays.asList(mockTraceFilter), mockAnnotationSubmitter);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullRandom() {
        new ClientTracerImpl(mockState, null, mockCollector, Arrays.asList(mockTraceFilter), mockAnnotationSubmitter);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullCollector() {
        new ClientTracerImpl(mockState, mockRandom, null, Arrays.asList(mockTraceFilter), mockAnnotationSubmitter);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullTraceFilter() {
        new ClientTracerImpl(mockState, mockRandom, mockCollector, null, mockAnnotationSubmitter);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullAnnotationSubmitter() {
        new ClientTracerImpl(mockState, mockRandom, mockCollector, Arrays.asList(mockTraceFilter), null);
    }

    @Test
    public void testSetClientSentNoClientSpan() {
        when(mockState.getCurrentClientSpan()).thenReturn(null);
        clientTracer.setClientSent();
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockAnnotationSubmitter);
    }

    @Test
    public void testSetClientSent() {

        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.setClientSent();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endPoint);
        expectedAnnotation.setValue(zipkinCoreConstants.CLIENT_SEND);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();
        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, endPoint, zipkinCoreConstants.CLIENT_SEND);
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2,
            mockAnnotationSubmitter);
    }

    @Test
    public void testSetClientReceivedNoClientSpan() {
        when(mockState.getCurrentClientSpan()).thenReturn(null);
        clientTracer.setClientReceived();
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2,
            mockAnnotationSubmitter);
    }

    @Test
    public void testSetClientReceived() {

        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.setClientReceived();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endPoint);
        expectedAnnotation.setValue(zipkinCoreConstants.CLIENT_RECV);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();
        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, endPoint, zipkinCoreConstants.CLIENT_RECV);
        verify(mockState).setCurrentClientSpan(null);
        verify(mockCollector).collect(mockSpan);
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2,
            mockAnnotationSubmitter);
    }

    @Test
    public void testStartNewSpanSampleFalse() {
        when(mockState.sample()).thenReturn(false);
        assertNull(clientTracer.startNewSpan(REQUEST_NAME));
        verify(mockState).sample();
        verify(mockState).setCurrentClientSpan(null);
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2,
            mockAnnotationSubmitter);
    }

    @Test
    public void testStartNewSpanSampleNullNotPartOfExistingSpan() {

        final ServerSpan mockServerSpan = mock(ServerSpan.class);
        when(mockServerSpan.getSpan()).thenReturn(null);
        when(mockState.sample()).thenReturn(null);
        when(mockState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockRandom.nextLong()).thenReturn(1l).thenReturn(2l);

        final SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertEquals(1l, newSpanId.getTraceId());
        assertEquals(1l, newSpanId.getSpanId());
        assertNull(newSpanId.getParentSpanId());

        final Span expectedSpan = new Span();
        expectedSpan.setTrace_id(1);
        expectedSpan.setId(1);
        expectedSpan.setName(REQUEST_NAME);

        verify(mockState).sample();
        verify(mockTraceFilter).trace(REQUEST_NAME);
        verify(mockTraceFilter2).trace(REQUEST_NAME);
        verify(mockRandom, times(1)).nextLong();
        verify(mockState).getCurrentServerSpan();
        verify(mockState).setCurrentClientSpan(expectedSpan);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2,
            mockAnnotationSubmitter);
    }

    @Test
    public void testStartNewSpanSampleTrueNotPartOfExistingSpan() {

        final ServerSpan mockServerSpan = mock(ServerSpan.class);
        when(mockServerSpan.getSpan()).thenReturn(null);
        when(mockState.sample()).thenReturn(true);
        when(mockState.getCurrentServerSpan()).thenReturn(mockServerSpan);
        when(mockRandom.nextLong()).thenReturn(1l).thenReturn(2l);

        final SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertEquals(1l, newSpanId.getTraceId());
        assertEquals(1l, newSpanId.getSpanId());
        assertNull(newSpanId.getParentSpanId());

        final Span expectedSpan = new Span();
        expectedSpan.setTrace_id(1);
        expectedSpan.setId(1);
        expectedSpan.setName(REQUEST_NAME);

        verify(mockState).sample();
        verify(mockRandom, times(1)).nextLong();
        verify(mockState).getCurrentServerSpan();
        verify(mockState).setCurrentClientSpan(expectedSpan);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2,
            mockAnnotationSubmitter);
    }

    @Test
    public void testStartNewSpanSampleTruePartOfExistingSpan() {

        when(mockState.sample()).thenReturn(true);

        final ServerSpan parentSpan = new ServerSpanImpl(PARENT_TRACE_ID, PARENT_SPAN_ID, null, "name");

        when(mockState.getCurrentServerSpan()).thenReturn(parentSpan);
        when(mockRandom.nextLong()).thenReturn(1l).thenReturn(2l);

        final SpanId newSpanId = clientTracer.startNewSpan(REQUEST_NAME);
        assertNotNull(newSpanId);
        assertEquals(PARENT_TRACE_ID, newSpanId.getTraceId());
        assertEquals(1l, newSpanId.getSpanId());
        assertEquals(Long.valueOf(PARENT_SPAN_ID), newSpanId.getParentSpanId());

        final Span expectedSpan = new Span();
        expectedSpan.setTrace_id(PARENT_TRACE_ID);
        expectedSpan.setId(1);
        expectedSpan.setParent_id(PARENT_SPAN_ID);
        expectedSpan.setName(REQUEST_NAME);

        verify(mockState).sample();
        verify(mockRandom, times(1)).nextLong();
        verify(mockState).getCurrentServerSpan();
        verify(mockState).setCurrentClientSpan(expectedSpan);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2,
            mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationClientSpanNull() {
        when(mockState.getCurrentClientSpan()).thenReturn(null);
        clientTracer.submitAnnotation(ANNOTATION_NAME, START_DATE, END_DATE);
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector);
    }

    @Test
    public void testSubmitAnnotationWithStartEndDate() {

        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.submitAnnotation(ANNOTATION_NAME, START_DATE, END_DATE);
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, endPoint, ANNOTATION_NAME, START_DATE, END_DATE);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationStringClientSpanNull() {
        when(mockState.getCurrentClientSpan()).thenReturn(null);
        clientTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockAnnotationSubmitter);
    }

    @Test
    public void testSubmitAnnotationString() {
        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.submitAnnotation(ANNOTATION_NAME);
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();

        verify(mockAnnotationSubmitter).submitAnnotation(mockSpan, endPoint, ANNOTATION_NAME);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockAnnotationSubmitter, mockSpan);
    }

    @Test
    public void testSubmitBinaryAnnotationStringValue() {
        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.submitBinaryAnnotation(KEY, STRING_VALUE);
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();

        verify(mockAnnotationSubmitter).submitBinaryAnnotation(mockSpan, endPoint, KEY, STRING_VALUE);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockAnnotationSubmitter, mockSpan);
    }

    @Test
    public void testSubmitBinaryAnnotationIntValue() {
        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        clientTracer.submitBinaryAnnotation(KEY, INT_VALUE);
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getEndPoint();

        verify(mockAnnotationSubmitter).submitBinaryAnnotation(mockSpan, endPoint, KEY, INT_VALUE);

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockAnnotationSubmitter, mockSpan);
    }

    @Test
    public void testFirstTraceFilterFalse() {
        when(mockState.sample()).thenReturn(null);
        when(mockTraceFilter.trace(REQUEST_NAME)).thenReturn(false);

        assertNull(clientTracer.startNewSpan(REQUEST_NAME));

        verify(mockState).sample();
        verify(mockTraceFilter).trace(REQUEST_NAME);
        verify(mockState).setCurrentClientSpan(null);

        verifyNoMoreInteractions(mockState, mockTraceFilter, mockTraceFilter2, mockRandom, mockCollector,
            mockAnnotationSubmitter);

    }

    @Test
    public void testSecondTraceFilterFalse() {
        when(mockState.sample()).thenReturn(null);
        when(mockTraceFilter2.trace(REQUEST_NAME)).thenReturn(false);

        assertNull(clientTracer.startNewSpan(REQUEST_NAME));

        verify(mockState).sample();
        verify(mockTraceFilter).trace(REQUEST_NAME);
        verify(mockTraceFilter2).trace(REQUEST_NAME);
        verify(mockState).setCurrentClientSpan(null);

        verifyNoMoreInteractions(mockState, mockTraceFilter, mockTraceFilter2, mockRandom, mockCollector,
            mockAnnotationSubmitter);

    }

}
