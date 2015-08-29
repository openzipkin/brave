package com.github.kristofa.brave;

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
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

@RunWith(PowerMockRunner.class)
@PrepareForTest(AnnotationSubmitter.class)
public class ServerTracerTest {

    private final static long CURRENT_TIME_MICROSECONDS = System.currentTimeMillis() * 1000;
    private final static long TRACE_ID = 1l;
    private final static long SPAN_ID = 2l;
    private final static Long PARENT_SPANID = 3l;
    private final static String SPAN_NAME = "span name";

    private ServerTracer serverTracer;
    private ServerSpanState mockServerSpanState;
    private SpanCollector mockSpanCollector;
    private ServerSpan mockServerSpan;
    private Span mockSpan;
    private Endpoint mockEndpoint;
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

        mockEndpoint = new Endpoint();
        mockRandom = mock(Random.class);
        mockTraceFilter1 = mock(TraceFilter.class);
        mockTraceFilter2 = mock(TraceFilter.class);

        traceFilters = Arrays.asList(mockTraceFilter1, mockTraceFilter2);

        PowerMockito.mockStatic(System.class);
        PowerMockito.when(System.currentTimeMillis()).thenReturn(CURRENT_TIME_MICROSECONDS / 1000);
        serverTracer = ServerTracer.builder()
            .state(mockServerSpanState)
            .randomGenerator(mockRandom)
            .spanCollector(mockSpanCollector)
            .traceFilters(traceFilters).build();
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
        final ServerSpan expectedServerSpan = ServerSpan.create(TRACE_ID, SPAN_ID, PARENT_SPANID, SPAN_NAME);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateNoTracing() {
        serverTracer.setStateNoTracing();
        final ServerSpan expectedServerSpan = ServerSpan.create(false);
        verify(mockServerSpanState).setCurrentServerSpan(expectedServerSpan);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector);
    }

    @Test
    public void testSetStateUnknownTraceFiltersTrue() {

        when(mockTraceFilter1.trace(SPAN_NAME)).thenReturn(true);
        when(mockTraceFilter2.trace(SPAN_NAME)).thenReturn(true);
        when(mockRandom.nextLong()).thenReturn(TRACE_ID);

        serverTracer.setStateUnknown(SPAN_NAME);
        final ServerSpan expectedServerSpan = ServerSpan.create(TRACE_ID, TRACE_ID, null, SPAN_NAME);

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

        final ServerSpan expectedServerSpan = ServerSpan.create(false);

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
        when(mockServerSpanState.getServerEndpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerReceived();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(mockEndpoint);
        expectedAnnotation.setValue(zipkinCoreConstants.SERVER_RECV);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        verify(mockServerSpanState).getCurrentServerSpan();
        verify(mockServerSpanState).getServerEndpoint();
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
        when(mockServerSpanState.getServerEndpoint()).thenReturn(mockEndpoint);
        serverTracer.setServerSend();

        final Annotation expectedAnnotation = new Annotation();
        expectedAnnotation.setHost(mockEndpoint);
        expectedAnnotation.setValue(zipkinCoreConstants.SERVER_SEND);
        expectedAnnotation.setTimestamp(CURRENT_TIME_MICROSECONDS);

        verify(mockServerSpanState, times(2)).getCurrentServerSpan();
        verify(mockServerSpanState).getServerEndpoint();

        verify(mockSpan).addToAnnotations(expectedAnnotation);
        verify(mockSpanCollector).collect(mockSpan);
        verify(mockServerSpanState).setCurrentServerSpan(null);
        verifyNoMoreInteractions(mockServerSpanState, mockSpanCollector, mockSpan);
    }



}
