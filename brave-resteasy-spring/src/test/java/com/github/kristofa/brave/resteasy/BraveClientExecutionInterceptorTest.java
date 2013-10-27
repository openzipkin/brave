package com.github.kristofa.brave.resteasy;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.MultivaluedMap;

import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.client.ClientResponse;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.interception.ClientExecutionContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.SpanId;

public class BraveClientExecutionInterceptorTest {

    private static final String URI = "http://localhost:8080/execute/this?a=b&c=d";
    private static final String HTTP_METHOD = "GET";
    private static final Integer OK_STATUS = 200;
    private static final String CUSTOM_SPAN_NAME = "ExecuteThisRequest";
    private static final String REQUEST_ANNOTATION = "request";
    private static final Long TRACE_ID = new Long(254656);
    private static final Long SPAN_ID = new Long(548999);
    private static final Long PARENT_SPAN_ID = new Long(44564);
    private static final Integer SERVER_ERROR_STATUS = 500;
    private ClientTracer mockClientTracer;
    private ClientExecutionContext mockExecutionContext;
    private ClientRequest mockClientRequest;
    private ClientResponse<?> mockClientResponse;
    private BraveClientExecutionInterceptor interceptor;

    @Before
    public void setup() throws Exception {
        mockClientTracer = mock(ClientTracer.class);
        mockExecutionContext = mock(ClientExecutionContext.class);
        mockClientRequest = buildClientRequest();
        when(mockExecutionContext.getRequest()).thenReturn(mockClientRequest);
        mockClientResponse = buildClientResponse();
        when(mockExecutionContext.proceed()).thenReturn(mockClientResponse);
        interceptor = new BraveClientExecutionInterceptor(mockClientTracer);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBraveClientExecutionInterceptor() {
        new BraveClientExecutionInterceptor(null);
    }

    @Test
    public void testExecuteNoTracingSpanNameSpecified() throws Exception {
        mockClientRequest.getHeaders().add(BraveHttpHeaders.SpanName.getName(), CUSTOM_SPAN_NAME);
        when(mockClientTracer.startNewSpan(CUSTOM_SPAN_NAME)).thenReturn(null);
        assertSame(mockClientResponse, interceptor.execute(mockExecutionContext));

        final InOrder inOrder = inOrder(mockClientTracer, mockClientRequest, mockExecutionContext);

        inOrder.verify(mockClientTracer).startNewSpan(CUSTOM_SPAN_NAME);
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.Sampled.getName(), "false");
        inOrder.verify(mockClientTracer).submitBinaryAnnotation(REQUEST_ANNOTATION, HTTP_METHOD + " " + URI);
        inOrder.verify(mockClientTracer).setClientSent();
        inOrder.verify(mockExecutionContext).proceed();
        inOrder.verify(mockClientTracer).setClientReceived();
        verifyNoMoreInteractions(mockClientTracer);
    }

    @Test
    public void testExecuteNoTracingSpanNameNotSpecified() throws Exception {

        when(mockClientTracer.startNewSpan(CUSTOM_SPAN_NAME)).thenReturn(null);
        assertSame(mockClientResponse, interceptor.execute(mockExecutionContext));

        final InOrder inOrder = inOrder(mockClientTracer, mockClientRequest, mockExecutionContext);

        inOrder.verify(mockClientTracer).startNewSpan("/execute/this");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.Sampled.getName(), "false");
        inOrder.verify(mockClientTracer).submitBinaryAnnotation(REQUEST_ANNOTATION, HTTP_METHOD + " " + URI);
        inOrder.verify(mockClientTracer).setClientSent();
        inOrder.verify(mockExecutionContext).proceed();
        inOrder.verify(mockClientTracer).setClientReceived();
        verifyNoMoreInteractions(mockClientTracer);
    }

    @Test
    public void testExecuteTracingNoParentSpan() throws Exception {

        final SpanId mockSpanId = mock(SpanId.class);
        when(mockSpanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockSpanId.getSpanId()).thenReturn(SPAN_ID);
        when(mockSpanId.getParentSpanId()).thenReturn(null);

        when(mockClientTracer.startNewSpan("/execute/this")).thenReturn(mockSpanId);
        assertSame(mockClientResponse, interceptor.execute(mockExecutionContext));

        final InOrder inOrder = inOrder(mockClientTracer, mockClientRequest, mockExecutionContext);

        inOrder.verify(mockClientTracer).startNewSpan("/execute/this");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.TraceId.getName(), TRACE_ID);
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.SpanId.getName(), SPAN_ID);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation(REQUEST_ANNOTATION, HTTP_METHOD + " " + URI);
        inOrder.verify(mockClientTracer).setClientSent();
        inOrder.verify(mockExecutionContext).proceed();
        inOrder.verify(mockClientTracer).setClientReceived();
        verifyNoMoreInteractions(mockClientTracer);
    }

    @Test
    public void testExecuteTracingWithParentSpan() throws Exception {

        final SpanId mockSpanId = mock(SpanId.class);
        when(mockSpanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockSpanId.getSpanId()).thenReturn(SPAN_ID);
        when(mockSpanId.getParentSpanId()).thenReturn(PARENT_SPAN_ID);

        when(mockClientTracer.startNewSpan("/execute/this")).thenReturn(mockSpanId);
        assertSame(mockClientResponse, interceptor.execute(mockExecutionContext));

        final InOrder inOrder = inOrder(mockClientTracer, mockClientRequest, mockExecutionContext);

        inOrder.verify(mockClientTracer).startNewSpan("/execute/this");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.TraceId.getName(), TRACE_ID);
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.SpanId.getName(), SPAN_ID);
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.ParentSpanId.getName(), PARENT_SPAN_ID);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation(REQUEST_ANNOTATION, HTTP_METHOD + " " + URI);
        inOrder.verify(mockClientTracer).setClientSent();
        inOrder.verify(mockExecutionContext).proceed();
        inOrder.verify(mockClientTracer).setClientReceived();
        verifyNoMoreInteractions(mockClientTracer);
    }

    @Test
    public void testExecuteHttpCodeNoSuccess() throws Exception {

        final SpanId mockSpanId = mock(SpanId.class);
        when(mockSpanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockSpanId.getSpanId()).thenReturn(SPAN_ID);
        when(mockSpanId.getParentSpanId()).thenReturn(PARENT_SPAN_ID);

        when(mockClientTracer.startNewSpan("/execute/this")).thenReturn(mockSpanId);
        when(mockClientResponse.getStatus()).thenReturn(SERVER_ERROR_STATUS);

        assertSame(mockClientResponse, interceptor.execute(mockExecutionContext));

        final InOrder inOrder = inOrder(mockClientTracer, mockClientRequest, mockExecutionContext);

        inOrder.verify(mockClientTracer).startNewSpan("/execute/this");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.TraceId.getName(), TRACE_ID);
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.SpanId.getName(), SPAN_ID);
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.ParentSpanId.getName(), PARENT_SPAN_ID);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation(REQUEST_ANNOTATION, HTTP_METHOD + " " + URI);
        inOrder.verify(mockClientTracer).setClientSent();
        inOrder.verify(mockExecutionContext).proceed();
        inOrder.verify(mockClientTracer).submitBinaryAnnotation("http.responsecode", SERVER_ERROR_STATUS);
        inOrder.verify(mockClientTracer).submitAnnotation("failure");
        inOrder.verify(mockClientTracer).setClientReceived();
        verifyNoMoreInteractions(mockClientTracer);

    }

    @Test
    public void testExecuteProceedThrowsException() throws Exception {

        final SpanId mockSpanId = mock(SpanId.class);
        when(mockSpanId.getTraceId()).thenReturn(TRACE_ID);
        when(mockSpanId.getSpanId()).thenReturn(SPAN_ID);
        when(mockSpanId.getParentSpanId()).thenReturn(PARENT_SPAN_ID);

        when(mockClientTracer.startNewSpan("/execute/this")).thenReturn(mockSpanId);
        final IllegalStateException exception = new IllegalStateException("Test exception");
        when(mockExecutionContext.proceed()).thenThrow(exception);

        try {
            interceptor.execute(mockExecutionContext);
            fail("Expected exception.");
        } catch (final IllegalStateException e) {
            assertSame(exception, e);
        }

        final InOrder inOrder = inOrder(mockClientTracer, mockClientRequest, mockExecutionContext);

        inOrder.verify(mockClientTracer).startNewSpan("/execute/this");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.Sampled.getName(), "true");
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.TraceId.getName(), TRACE_ID);
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.SpanId.getName(), SPAN_ID);
        inOrder.verify(mockClientRequest).header(BraveHttpHeaders.ParentSpanId.getName(), PARENT_SPAN_ID);
        inOrder.verify(mockClientTracer).submitBinaryAnnotation(REQUEST_ANNOTATION, HTTP_METHOD + " " + URI);
        inOrder.verify(mockClientTracer).setClientSent();
        inOrder.verify(mockExecutionContext).proceed();
        inOrder.verify(mockClientTracer).submitAnnotation("failure");
        inOrder.verify(mockClientTracer).setClientReceived();
        verifyNoMoreInteractions(mockClientTracer);

    }

    private ClientRequest buildClientRequest() throws Exception {
        final ClientRequest request = mock(ClientRequest.class);
        final MultivaluedMap<String, String> headerMap = new MultivaluedMapImpl<String, String>();
        when(request.getHeaders()).thenReturn(headerMap);
        when(request.getUri()).thenReturn(URI);
        when(request.getHttpMethod()).thenReturn(HTTP_METHOD);
        return request;
    }

    private ClientResponse<?> buildClientResponse() {
        @SuppressWarnings("unchecked")
        final ClientResponse<String> response = mock(ClientResponse.class);
        when(response.getStatus()).thenReturn(OK_STATUS);

        return response;
    }

}
