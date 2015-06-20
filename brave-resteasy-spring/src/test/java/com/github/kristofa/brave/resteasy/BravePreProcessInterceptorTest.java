package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndpointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.client.ClientRequestHeaders;
import org.jboss.resteasy.client.ClientRequest;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class BravePreProcessInterceptorTest {

    private final static long TRACE_ID = -10l;
    private final static long SPAN_ID = 11l;
    private final static long PARENT_SPAN_ID = 12l;
    private static final String PATH = "/PATH";
    private static final String LOCAL_ADDR = "localhost";
    private static final int PORT = 80;
    private static final String CONTEXT_PATH = "contextPath";
    private static final String SPAN_NAME = "SPAN_NAME";

    private BravePreProcessInterceptor interceptor;
    private EndpointSubmitter mockEndpointSubmitter;
    private ServerTracer mockServerTracer;
    private HttpServletRequest mockHttpServletRequest;
    private HttpRequest mockHttpRequest;

    @Before
    public void setUp() throws Exception {
        mockEndpointSubmitter = mock(EndpointSubmitter.class);
        mockServerTracer = mock(ServerTracer.class);
        mockHttpServletRequest = mock(HttpServletRequest.class);
        interceptor = new BravePreProcessInterceptor(mockEndpointSubmitter, mockServerTracer);
        interceptor.servletRequest = mockHttpServletRequest;

        mockHttpRequest = mock(HttpRequest.class);
        when(mockHttpRequest.getPreprocessedPath()).thenReturn(PATH);

        when(mockHttpServletRequest.getLocalAddr()).thenReturn(LOCAL_ADDR);
        when(mockHttpServletRequest.getLocalPort()).thenReturn(PORT);
        when(mockHttpServletRequest.getContextPath()).thenReturn(CONTEXT_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void testBravePreProcessInterceptorNullEndpointSubmitter() {
        new BravePreProcessInterceptor(null, mockServerTracer);
    }

    @Test(expected = NullPointerException.class)
    public void testBravePreProcessInterceptorNullServerTracer() {
        new BravePreProcessInterceptor(mockEndpointSubmitter, null);
    }

    @Test
    public void testPreProcessEndpointNotSet_TraceHeadersSubmitted() {

        when(mockEndpointSubmitter.endpointSubmitted()).thenReturn(false);
        mockHttpHeaders(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, null, true);

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndpointSubmitter, mockServerTracer);
        inOrder.verify(mockEndpointSubmitter).endpointSubmitted();
        inOrder.verify(mockEndpointSubmitter).submit(LOCAL_ADDR, PORT, CONTEXT_PATH);
        inOrder.verify(mockServerTracer).clearCurrentSpan();
        inOrder.verify(mockServerTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, PATH);
        inOrder.verify(mockServerTracer).setServerReceived();

        verify(mockHttpRequest).getPreprocessedPath();
        verify(mockHttpRequest).getHttpHeaders();
        verify(mockHttpServletRequest).getLocalAddr();
        verify(mockHttpServletRequest).getLocalPort();
        verify(mockHttpServletRequest).getContextPath();

        verifyNoMoreInteractions(mockEndpointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);

    }

    @Test
    public void testPreProcessEndpointSet_TraceHeadersSubmitted() {
        when(mockEndpointSubmitter.endpointSubmitted()).thenReturn(true);
        mockHttpHeaders(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, null, true);

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndpointSubmitter, mockServerTracer);
        inOrder.verify(mockEndpointSubmitter).endpointSubmitted();
        inOrder.verify(mockServerTracer).clearCurrentSpan();
        inOrder.verify(mockServerTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, PATH);
        inOrder.verify(mockServerTracer).setServerReceived();

        verify(mockHttpRequest).getPreprocessedPath();
        verify(mockHttpRequest).getHttpHeaders();

        verifyNoMoreInteractions(mockEndpointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);
    }

    @Test
    public void testPreProcessEndpointSet_TraceHeadersNotSubmitted() {
        when(mockEndpointSubmitter.endpointSubmitted()).thenReturn(true);
        mockEmptyHttpHeaders();

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndpointSubmitter, mockServerTracer);
        inOrder.verify(mockEndpointSubmitter).endpointSubmitted();
        inOrder.verify(mockServerTracer).clearCurrentSpan();

        inOrder.verify(mockServerTracer).setStateUnknown(PATH);
        inOrder.verify(mockServerTracer).setServerReceived();

        verify(mockHttpRequest).getPreprocessedPath();
        verify(mockHttpRequest).getHttpHeaders();

        verifyNoMoreInteractions(mockEndpointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);
    }

    @Test
    public void testPreProcessEndpointSet_ShouldNotTrace() {
        when(mockEndpointSubmitter.endpointSubmitted()).thenReturn(true);
        mockShouldNotTraceHttpHeaders();

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndpointSubmitter, mockServerTracer);
        inOrder.verify(mockEndpointSubmitter).endpointSubmitted();
        inOrder.verify(mockServerTracer).clearCurrentSpan();
        inOrder.verify(mockServerTracer).setStateNoTracing();

        verify(mockHttpRequest).getHttpHeaders();

        verifyNoMoreInteractions(mockEndpointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);
    }

    @Test
    public void testPreProcessSpanNameDefined() {
        when(mockEndpointSubmitter.endpointSubmitted()).thenReturn(true);
        mockHttpHeaders(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SPAN_NAME, true);

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndpointSubmitter, mockServerTracer);
        inOrder.verify(mockEndpointSubmitter).endpointSubmitted();
        inOrder.verify(mockServerTracer).clearCurrentSpan();
        inOrder.verify(mockServerTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SPAN_NAME);
        inOrder.verify(mockServerTracer).setServerReceived();

        verify(mockHttpRequest).getHttpHeaders();

        verifyNoMoreInteractions(mockEndpointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);
    }

    private void mockHttpHeaders(final long traceid, final long spanId, final long parentSpanId, final String spanName,
                                 final Boolean sampled) {
        final HttpHeaders mockHttpHeaders = mock(HttpHeaders.class);

        ClientRequest clientRequest = new ClientRequest("test");
        RestEasyClientRequestAdapter request = new RestEasyClientRequestAdapter(clientRequest);
        SpanId span;
        if (sampled) {
            span = mock(SpanId.class);
            when(span.getTraceId()).thenReturn(traceid);
            when(span.getSpanId()).thenReturn(spanId);
            when(span.getParentSpanId()).thenReturn(parentSpanId);
        } else {
            span = null;
        }
        ClientRequestHeaders.addTracingHeaders(request, span, spanName);

        when(mockHttpHeaders.getRequestHeaders()).thenReturn(clientRequest.getHeaders());
        when(mockHttpRequest.getHttpHeaders()).thenReturn(mockHttpHeaders);
    }

    private void mockEmptyHttpHeaders() {
        final HttpHeaders mockHttpHeaders = mock(HttpHeaders.class);
        final MultivaluedMapImpl<String, String> multivaluedMapImpl = new MultivaluedMapImpl<String, String>();
        when(mockHttpHeaders.getRequestHeaders()).thenReturn(multivaluedMapImpl);
        when(mockHttpRequest.getHttpHeaders()).thenReturn(mockHttpHeaders);
    }

    private void mockShouldNotTraceHttpHeaders() {
        final HttpHeaders mockHttpHeaders = mock(HttpHeaders.class);
        final MultivaluedMapImpl<String, String> multivaluedMapImpl = new MultivaluedMapImpl<String, String>();
        multivaluedMapImpl.add(BraveHttpHeaders.Sampled.getName(), "false");
        when(mockHttpHeaders.getRequestHeaders()).thenReturn(multivaluedMapImpl);
        when(mockHttpRequest.getHttpHeaders()).thenReturn(mockHttpHeaders);
    }

}
