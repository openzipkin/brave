package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.mockito.Mockito.*;

public class ServerRequestInterceptorTest {

    private final static String SPAN_NAME = "getUsers";
    private final static long TRACE_ID = 3425;
    private final static long SPAN_ID = 43435;
    private final static long PARENT_SPAN_ID = 44334435;
    private final static String REQUEST_NAME = "/user/1343";

    private ServerRequestInterceptor interceptor;
    private ServerTracer serverTracer;
    private ServerRequestAdapter adapter;

    @Before
    public void setup() {
        serverTracer = mock(ServerTracer.class);
        interceptor = new ServerRequestInterceptor(serverTracer);
        adapter = mock(ServerRequestAdapter.class);
    }

    @Test
    public void handleSampleFalse() {
        TraceData traceData = new TraceData.Builder().sample(false).build();
        when(adapter.getTraceData()).thenReturn(traceData);
        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateNoTracing();
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void handleNoState() {
        TraceData traceData = new TraceData.Builder().build();
        when(adapter.getTraceData()).thenReturn(traceData);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.getRequestRepresentation()).thenReturn(Optional.empty());

        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateUnknown(SPAN_NAME);
        inOrder.verify(serverTracer).setServerReceived();
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void handleSampleRequestWithParentSpanId() {
        TraceData traceData = new TraceData.Builder().spanId(new SpanId(TRACE_ID, SPAN_ID, Optional.of(PARENT_SPAN_ID))).sample(true).build();
        when(adapter.getTraceData()).thenReturn(traceData);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.getRequestRepresentation()).thenReturn(Optional.of(REQUEST_NAME));

        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SPAN_NAME);
        inOrder.verify(serverTracer).setServerReceived();
        inOrder.verify(serverTracer).submitBinaryAnnotation("request", REQUEST_NAME);
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void handleSampleRequestWithoutParentSpanId() {
        TraceData traceData = new TraceData.Builder().spanId(new SpanId(TRACE_ID, SPAN_ID, Optional.empty())).sample(true).build();
        when(adapter.getTraceData()).thenReturn(traceData);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.getRequestRepresentation()).thenReturn(Optional.empty());

        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, null, SPAN_NAME);
        inOrder.verify(serverTracer).setServerReceived();
        verifyNoMoreInteractions(serverTracer);
    }
}
