package com.github.kristofa.brave.client;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.google.common.base.Optional;
import org.apache.http.HttpException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import javax.xml.ws.http.HTTPException;
import java.io.IOException;
import java.net.URI;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ClientRequestInterceptorTest {
    private static final String SERVICE_NAME = "service";
    private static final String CONTEXT = "context";
    private static final String PATH = "/path/path2";
    private static final String FULL_PATH = "/" + CONTEXT + PATH;
    private static final String METHOD = "GET";
    private static final Long SPAN_ID = 85446l;
    private static final Long PARENT_SPAN_ID = 58848l;
    private static final Long TRACE_ID = 54646l;

    private ClientRequestInterceptor interceptor;
    private ClientTracer mockClientTracer;
    private ClientRequestAdapter clientRequestAdapter;

    @Before
    public void setUp() throws Exception {
        mockClientTracer = mock(ClientTracer.class);
        interceptor = new ClientRequestInterceptor(mockClientTracer);
        clientRequestAdapter = mock(ClientRequestAdapter.class);
        when(clientRequestAdapter.getUri()).thenReturn(URI.create(FULL_PATH));
        when(clientRequestAdapter.getMethod()).thenReturn(METHOD);
        when(clientRequestAdapter.getSpanName()).thenReturn(Optional.<String>absent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBraveHttpRequestInterceptor() {
        new ClientRequestInterceptor(null);
    }

    @Test
    public void testProcessNoTracing() throws HttpException, IOException {
        when(mockClientTracer.startNewSpan(PATH)).thenReturn(null);

        interceptor.handle(clientRequestAdapter, Optional.<String>absent());

        final InOrder inOrder = inOrder(mockClientTracer, clientRequestAdapter);

        inOrder.verify(mockClientTracer).startNewSpan(PATH);
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.Sampled.getName(), "false");
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(CONTEXT);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);
        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer);
    }

    @Test
    public void testProcessTracing() throws HttpException, IOException {
        final SpanId spanId = mock(SpanId.class);
        when(spanId.getSpanId()).thenReturn(SPAN_ID);
        when(spanId.getParentSpanId()).thenReturn(PARENT_SPAN_ID);
        when(spanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockClientTracer.startNewSpan(PATH)).thenReturn(spanId);

        interceptor.handle(clientRequestAdapter, Optional.<String>absent());

        final InOrder inOrder = inOrder(mockClientTracer, clientRequestAdapter);

        inOrder.verify(mockClientTracer).startNewSpan(PATH);
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.ParentSpanId.getName(), Long.toString(PARENT_SPAN_ID, 16));
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(CONTEXT);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);
        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer);
    }

    @Test
    public void testProcessTracingNoParentId() throws HttpException, IOException {
        final SpanId spanId = mock(SpanId.class);
        when(spanId.getSpanId()).thenReturn(SPAN_ID);
        when(spanId.getParentSpanId()).thenReturn(null);
        when(spanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockClientTracer.startNewSpan(PATH)).thenReturn(spanId);

        interceptor.handle(clientRequestAdapter, Optional.<String>absent());

        final InOrder inOrder = inOrder(mockClientTracer, clientRequestAdapter);

        inOrder.verify(mockClientTracer).startNewSpan(PATH);
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16));
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(CONTEXT);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);
        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer);
    }

    @Test
    public void testHandleTracingWithServiceNameOverride() throws HTTPException, IOException {
        final SpanId spanId = mock(SpanId.class);
        when(spanId.getSpanId()).thenReturn(SPAN_ID);
        when(spanId.getParentSpanId()).thenReturn(PARENT_SPAN_ID);
        when(spanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockClientTracer.startNewSpan(FULL_PATH)).thenReturn(spanId);

        interceptor.handle(clientRequestAdapter, Optional.of(SERVICE_NAME));

        final InOrder inOrder = inOrder(mockClientTracer, clientRequestAdapter);

        inOrder.verify(mockClientTracer).startNewSpan(FULL_PATH);
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.ParentSpanId.getName(), Long.toString(PARENT_SPAN_ID, 16));
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(SERVICE_NAME);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);
        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer);
    }
}
