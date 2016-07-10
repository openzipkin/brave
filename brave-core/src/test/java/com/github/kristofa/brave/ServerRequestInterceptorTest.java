package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class ServerRequestInterceptorTest {

    private final static String SPAN_NAME = "getUsers";
    private final static long TRACE_ID = 3425;
    private final static long SPAN_ID = 43435;
    private final static long PARENT_SPAN_ID = 44334435;
    private static final KeyValueAnnotation ANNOTATION1 = KeyValueAnnotation.create(zipkin.TraceKeys.HTTP_URL, "/orders/user/4543");
    private static final KeyValueAnnotation ANNOTATION2 = KeyValueAnnotation.create("http.code", "200");

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
        TraceData traceData = TraceData.builder().sample(false).build();
        when(adapter.getTraceData()).thenReturn(traceData);
        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateNoTracing();
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void handleNoState() {
        TraceData traceData = TraceData.builder().build();
        when(adapter.getTraceData()).thenReturn(traceData);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Collections.EMPTY_LIST);

        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateUnknown(SPAN_NAME);
        inOrder.verify(serverTracer).setServerReceived();
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void handleSampleRequestWithParentSpanId() {
        SpanId spanId =
            SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(PARENT_SPAN_ID).build();
        TraceData traceData = TraceData.builder().spanId(spanId).sample(true).build();
        when(adapter.getTraceData()).thenReturn(traceData);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));

        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer, adapter);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SPAN_NAME);
        inOrder.verify(serverTracer).setServerReceived();
        inOrder.verify(adapter).requestAnnotations();
        inOrder.verify(serverTracer).submitBinaryAnnotation(ANNOTATION1.getKey(), ANNOTATION1.getValue());
        inOrder.verify(serverTracer).submitBinaryAnnotation(ANNOTATION2.getKey(), ANNOTATION2.getValue());
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void handleSampleRequestWithoutParentSpanId() {
        SpanId spanId = SpanId.builder().traceId(TRACE_ID).spanId(SPAN_ID).parentId(null).build();
        TraceData traceData = TraceData.builder().spanId(spanId).sample(true).build();
        when(adapter.getTraceData()).thenReturn(traceData);
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Collections.EMPTY_LIST);

        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(serverTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, null, SPAN_NAME);
        inOrder.verify(serverTracer).setServerReceived();
        verifyNoMoreInteractions(serverTracer);
    }
}
