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
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.class)
public class ClientTracerTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private final static String REQUEST_NAME = "requestName";
    private static final long PARENT_SPAN_ID = 103;
    private static final long PARENT_TRACE_ID = 105;

    private ServerAndClientSpanState mockState;
    private Random mockRandom;
    private SpanCollector mockCollector;
    private ClientTracer clientTracer;
    private Span mockSpan;
    private Endpoint endpoint;
    private TraceFilter mockTraceFilter;
    private TraceFilter mockTraceFilter2;

    @Before
    public void setup() {
        mockState = mock(ServerAndClientSpanState.class);
        endpoint = new Endpoint();
        mockTraceFilter = mock(TraceFilter.class);
        mockTraceFilter2 = mock(TraceFilter.class);
        when(mockState.getServerEndpoint()).thenReturn(endpoint);
        when(mockTraceFilter.trace(REQUEST_NAME)).thenReturn(true);
        when(mockTraceFilter2.trace(REQUEST_NAME)).thenReturn(true);

        mockRandom = mock(Random.class);
        mockCollector = mock(SpanCollector.class);
        mockSpan = mock(Span.class);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
        clientTracer = ClientTracer.builder()
            .state(mockState)
            .randomGenerator(mockRandom)
            .spanCollector(mockCollector)
            .traceFilters(Arrays.asList(mockTraceFilter, mockTraceFilter2))
            .build();
    }

    @Test
    public void testSetClientSentNoClientSpan() {
        when(mockState.getCurrentClientSpan()).thenReturn(null);
        clientTracer.setClientSent();
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter);
    }

    @Test
    public void testSetClientSent() {

        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        when(mockState.getClientEndpoint()).thenReturn(endpoint);
        clientTracer.setClientSent();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endpoint);
        expectedAnnotation.setValue(zipkinCoreConstants.CLIENT_SEND);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);
        verify(mockState).getCurrentClientSpan();
        verify(mockState).getClientEndpoint();
        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verifyNoMoreInteractions(mockState, mockRandom, mockSpan, mockCollector, mockTraceFilter, mockTraceFilter2);
    }

    @Test
    public void testSetClientReceivedNoClientSpan() {
        when(mockState.getCurrentClientSpan()).thenReturn(null);
        clientTracer.setClientReceived();
        verify(mockState).getCurrentClientSpan();
        verifyNoMoreInteractions(mockState, mockSpan, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2);
    }

    @Test
    public void testSetClientReceived() {

        when(mockState.getCurrentClientSpan()).thenReturn(mockSpan);
        when(mockState.getClientEndpoint()).thenReturn(endpoint);
        clientTracer.setClientReceived();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(endpoint);
        expectedAnnotation.setValue(zipkinCoreConstants.CLIENT_RECV);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);
        verify(mockState, times(2)).getCurrentClientSpan();
        verify(mockState).getClientEndpoint();
        verify(mockState).setCurrentClientSpan(null);
        verify(mockState).setCurrentClientServiceName(null);
        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verify(mockCollector).collect(mockSpan);
        verifyNoMoreInteractions(mockState, mockSpan, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2);
    }

    @Test
    public void testStartNewSpanSampleFalse() {
        when(mockState.sample()).thenReturn(false);
        assertNull(clientTracer.startNewSpan(REQUEST_NAME));
        verify(mockState).sample();
        verify(mockState).setCurrentClientSpan(null);
        verify(mockState).setCurrentClientServiceName(null);
        verifyNoMoreInteractions(mockState, mockSpan, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2);
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

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2);
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

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2);
    }

    @Test
    public void testStartNewSpanSampleTruePartOfExistingSpan() {

        when(mockState.sample()).thenReturn(true);

        final ServerSpan parentSpan = ServerSpan.create(PARENT_TRACE_ID, PARENT_SPAN_ID, null, "name");

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

        verifyNoMoreInteractions(mockState, mockRandom, mockCollector, mockTraceFilter, mockTraceFilter2);
    }

    @Test
    public void testFirstTraceFilterFalse() {
        when(mockState.sample()).thenReturn(null);
        when(mockTraceFilter.trace(REQUEST_NAME)).thenReturn(false);

        assertNull(clientTracer.startNewSpan(REQUEST_NAME));

        verify(mockState).sample();
        verify(mockTraceFilter).trace(REQUEST_NAME);
        verify(mockState).setCurrentClientSpan(null);
        verify(mockState).setCurrentClientServiceName(null);
        verifyNoMoreInteractions(mockState, mockTraceFilter, mockTraceFilter2, mockRandom, mockCollector);

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
        verify(mockState).setCurrentClientServiceName(null);
        verifyNoMoreInteractions(mockState, mockTraceFilter, mockTraceFilter2, mockRandom, mockCollector);

    }

}
