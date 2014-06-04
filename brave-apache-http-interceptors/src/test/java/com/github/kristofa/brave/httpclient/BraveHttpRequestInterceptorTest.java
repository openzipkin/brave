package com.github.kristofa.brave.httpclient;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;

public class BraveHttpRequestInterceptorTest {

    private static final String CONTEXT = "context";
    private static final String PATH = "/path/path2";
    private static final String FULL_PATH = "/" + CONTEXT + PATH;
    private static final String METHOD = "GET";
    private static final Long SPAN_ID = 85446l;
    private static final Long PARENT_SPAN_ID = 58848l;
    private static final Long TRACE_ID = 54646l;

    private BraveHttpRequestInterceptor interceptor;
    private ClientTracer mockClientTracer;
    private HttpRequest httpRequest;
    private HttpContext mockHttpContext;

    @Before
    public void setUp() throws Exception {
        mockClientTracer = mock(ClientTracer.class);
        interceptor = new BraveHttpRequestInterceptor(mockClientTracer);
        httpRequest = mock(HttpRequest.class);
        final RequestLine mockRequestLine = mock(RequestLine.class);
        when(mockRequestLine.getUri()).thenReturn(FULL_PATH);
        when(mockRequestLine.getMethod()).thenReturn(METHOD);
        when(httpRequest.getRequestLine()).thenReturn(mockRequestLine);

        mockHttpContext = mock(HttpContext.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBraveHttpRequestInterceptor() {
        new BraveHttpRequestInterceptor(null);
    }

    @Test
    public void testProcessNoTracing() throws HttpException, IOException {

        when(mockClientTracer.startNewSpan(PATH)).thenReturn(null);

        interceptor.process(httpRequest, mockHttpContext);

        final InOrder inOrder = inOrder(mockClientTracer, httpRequest);

        inOrder.verify(mockClientTracer).startNewSpan(PATH);
        inOrder.verify(httpRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "false");
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

        interceptor.process(httpRequest, mockHttpContext);

        final InOrder inOrder = inOrder(mockClientTracer, httpRequest);

        inOrder.verify(mockClientTracer).startNewSpan(PATH);
        inOrder.verify(httpRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(httpRequest).addHeader(BraveHttpHeaders.TraceId.getName(), String.valueOf(TRACE_ID));
        inOrder.verify(httpRequest).addHeader(BraveHttpHeaders.SpanId.getName(), String.valueOf(SPAN_ID));
        inOrder.verify(httpRequest).addHeader(BraveHttpHeaders.ParentSpanId.getName(), String.valueOf(PARENT_SPAN_ID));
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

        interceptor.process(httpRequest, mockHttpContext);

        final InOrder inOrder = inOrder(mockClientTracer, httpRequest);

        inOrder.verify(mockClientTracer).startNewSpan(PATH);
        inOrder.verify(httpRequest).addHeader(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(httpRequest).addHeader(BraveHttpHeaders.TraceId.getName(), String.valueOf(TRACE_ID));
        inOrder.verify(httpRequest).addHeader(BraveHttpHeaders.SpanId.getName(), String.valueOf(SPAN_ID));
        inOrder.verify(mockClientTracer).setCurrentClientServiceName(CONTEXT);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("request", METHOD + " " + FULL_PATH);
        inOrder.verify(mockClientTracer).setClientSent();
        verifyNoMoreInteractions(mockClientTracer);

    }

}
