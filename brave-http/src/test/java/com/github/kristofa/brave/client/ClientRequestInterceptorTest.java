package com.github.kristofa.brave.client;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;

import javax.xml.ws.http.HTTPException;

import org.apache.http.HttpException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;

public class ClientRequestInterceptorTest {

    private static final String SERVICE_NAME = "service";
    private static final String CONTEXT = "context";
    private static final String PATH = "/path/path2";
    private static final String FULL_PATH = "/" + CONTEXT + PATH;
    private static final String FILTERED_PATH = "/path/<numeric>";
    private static final String METHOD = "GET";
    private static final Long SPAN_ID = 85446l;
    private static final Long PARENT_SPAN_ID = 58848l;
    private static final Long TRACE_ID = 54646l;

    private ClientRequestInterceptor interceptor;
    private ClientTracer mockClientTracer;
    private ClientRequestAdapter clientRequestAdapter;
    private SpanNameFilter mockSpanNameFilter;

    @Before
    public void setUp() throws Exception {
        mockClientTracer = mock(ClientTracer.class);
        mockSpanNameFilter = mock(SpanNameFilter.class);
        interceptor = new ClientRequestInterceptor(mockClientTracer, null);
        clientRequestAdapter = mock(ClientRequestAdapter.class);
        when(clientRequestAdapter.getUri()).thenReturn(URI.create(FULL_PATH));
        when(clientRequestAdapter.getMethod()).thenReturn(METHOD);
        when(clientRequestAdapter.getSpanName()).thenReturn(null);
    }

    @Test(expected = NullPointerException.class)
    public void testBraveHttpRequestInterceptor() {
        new ClientRequestInterceptor(null, mockSpanNameFilter);
    }

    @Test
    public void testProcessNoTracing() throws HttpException, IOException {
        when(mockClientTracer.startNewSpan(PATH)).thenReturn(null);

        interceptor.handle(clientRequestAdapter, null);

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

        interceptor.handle(clientRequestAdapter, null);

        final InOrder inOrder = inOrder(mockClientTracer, clientRequestAdapter);

        inOrder.verify(mockClientTracer).startNewSpan(PATH);
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.ParentSpanId.getName(),
            Long.toString(PARENT_SPAN_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanName.getName(), PATH);
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(CONTEXT);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);
        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer, mockSpanNameFilter);
    }

    @Test
    public void testProcessTracingNoParentId() throws HttpException, IOException {
        final SpanId spanId = mock(SpanId.class);
        when(spanId.getSpanId()).thenReturn(SPAN_ID);
        when(spanId.getParentSpanId()).thenReturn(null);
        when(spanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockClientTracer.startNewSpan(PATH)).thenReturn(spanId);

        interceptor.handle(clientRequestAdapter, null);

        final InOrder inOrder = inOrder(mockClientTracer, clientRequestAdapter);

        inOrder.verify(mockClientTracer).startNewSpan(PATH);
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanName.getName(), PATH);
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(CONTEXT);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);

        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer, mockSpanNameFilter);
    }

    @Test
    public void testHandleTracingWithServiceNameOverride() throws HTTPException, IOException {
        final SpanId spanId = mock(SpanId.class);
        when(spanId.getSpanId()).thenReturn(SPAN_ID);
        when(spanId.getParentSpanId()).thenReturn(PARENT_SPAN_ID);
        when(spanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockClientTracer.startNewSpan(FULL_PATH)).thenReturn(spanId);

        interceptor.handle(clientRequestAdapter, SERVICE_NAME);

        final InOrder inOrder = inOrder(mockClientTracer, clientRequestAdapter);

        inOrder.verify(mockClientTracer).startNewSpan(FULL_PATH);
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.ParentSpanId.getName(),
            Long.toString(PARENT_SPAN_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanName.getName(), FULL_PATH);
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(SERVICE_NAME);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);
        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer, mockSpanNameFilter);
    }

    @Test
    public void testHandleServiceWithSpanNameFilter() {
        final SpanId spanId = mock(SpanId.class);
        when(spanId.getSpanId()).thenReturn(SPAN_ID);
        when(spanId.getParentSpanId()).thenReturn(PARENT_SPAN_ID);
        when(spanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockClientTracer.startNewSpan(FILTERED_PATH)).thenReturn(spanId);
        when(mockSpanNameFilter.filterSpanName(PATH)).thenReturn(FILTERED_PATH);

        interceptor = new ClientRequestInterceptor(mockClientTracer, mockSpanNameFilter);
        interceptor.handle(clientRequestAdapter, null);

        final InOrder inOrder = inOrder(mockClientTracer, clientRequestAdapter, mockSpanNameFilter);
        inOrder.verify(mockSpanNameFilter).filterSpanName(PATH);
        inOrder.verify(mockClientTracer).startNewSpan(FILTERED_PATH);
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.TraceId.getName(), Long.toString(TRACE_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanId.getName(), Long.toString(SPAN_ID, 16));
        inOrder.verify(clientRequestAdapter).addHeader(BraveHttpHeaders.SpanName.getName(), FILTERED_PATH);
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(CONTEXT);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);
        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer, mockSpanNameFilter);
    }
}