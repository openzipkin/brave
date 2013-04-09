package com.github.kristofa.brave.resteasy;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.HttpHeaders;

import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.github.kristofa.brave.EndPoint;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.HeaderConstants;
import com.github.kristofa.brave.ServerTracer;

public class BravePreProcessInterceptorTest {

    private final static long TRACE_ID = 10l;
    private final static long SPAN_ID = 11l;
    private final static long PARENT_SPAN_ID = 12l;
    private static final String PATH = "PATH";
    private static final String LOCAL_ADDR = "localhost";
    private static final int PORT = 80;
    private static final String CONTEXT_PATH = "contextPath";

    private BravePreProcessInterceptor interceptor;
    private EndPointSubmitter mockEndPointSubmitter;
    private ServerTracer mockServerTracer;
    private HttpServletRequest mockHttpServletRequest;
    private EndPoint mockEndPoint;
    private HttpRequest mockHttpRequest;

    @Before
    public void setUp() throws Exception {
        mockEndPointSubmitter = mock(EndPointSubmitter.class);
        mockServerTracer = mock(ServerTracer.class);
        mockHttpServletRequest = mock(HttpServletRequest.class);
        interceptor = new BravePreProcessInterceptor(mockEndPointSubmitter, mockServerTracer);
        interceptor.servletRequest = mockHttpServletRequest;

        mockEndPoint = mock(EndPoint.class);
        mockHttpRequest = mock(HttpRequest.class);
        when(mockHttpRequest.getPreprocessedPath()).thenReturn(PATH);

        when(mockHttpServletRequest.getLocalAddr()).thenReturn(LOCAL_ADDR);
        when(mockHttpServletRequest.getLocalPort()).thenReturn(PORT);
        when(mockHttpServletRequest.getContextPath()).thenReturn(CONTEXT_PATH);
    }

    @Test(expected = NullPointerException.class)
    public void testBravePreProcessInterceptorNullEndPointSubmitter() {
        new BravePreProcessInterceptor(null, mockServerTracer);
    }

    @Test(expected = NullPointerException.class)
    public void testBravePreProcessInterceptorNullServerTracer() {
        new BravePreProcessInterceptor(mockEndPointSubmitter, null);
    }

    @Test
    public void testPreProcessEndPointNotSet_TraceHeadersSubmitted() {

        when(mockEndPointSubmitter.getEndPoint()).thenReturn(null);
        mockHttpHeaders(TRACE_ID, SPAN_ID, PARENT_SPAN_ID);

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndPointSubmitter, mockServerTracer);
        inOrder.verify(mockEndPointSubmitter).getEndPoint();
        inOrder.verify(mockEndPointSubmitter).submit(LOCAL_ADDR, PORT, CONTEXT_PATH);
        inOrder.verify(mockServerTracer).setShouldTrace(true);
        inOrder.verify(mockServerTracer).setSpan(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, PATH);
        inOrder.verify(mockServerTracer).setServerReceived();

        verify(mockHttpRequest).getPreprocessedPath();
        verify(mockHttpRequest).getHttpHeaders();
        verify(mockHttpServletRequest).getLocalAddr();
        verify(mockHttpServletRequest).getLocalPort();
        verify(mockHttpServletRequest).getContextPath();

        verifyNoMoreInteractions(mockEndPointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);

    }

    @Test
    public void testPreProcessEndPointSet_TraceHeadersSubmitted() {
        when(mockEndPointSubmitter.getEndPoint()).thenReturn(mockEndPoint);
        mockHttpHeaders(TRACE_ID, SPAN_ID, PARENT_SPAN_ID);

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndPointSubmitter, mockServerTracer);
        inOrder.verify(mockEndPointSubmitter).getEndPoint();
        inOrder.verify(mockServerTracer).setShouldTrace(true);
        inOrder.verify(mockServerTracer).setSpan(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, PATH);
        inOrder.verify(mockServerTracer).setServerReceived();

        verify(mockHttpRequest).getPreprocessedPath();
        verify(mockHttpRequest).getHttpHeaders();

        verifyNoMoreInteractions(mockEndPointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);
    }

    @Test
    public void testPreProcessEndPointSet_TraceHeadersNotSubmitted() {
        when(mockEndPointSubmitter.getEndPoint()).thenReturn(mockEndPoint);
        mockEmptyHttpHeaders();

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndPointSubmitter, mockServerTracer);
        inOrder.verify(mockEndPointSubmitter).getEndPoint();
        inOrder.verify(mockServerTracer).setShouldTrace(true);
        inOrder.verify(mockServerTracer).clearCurrentSpan();

        verify(mockHttpRequest).getHttpHeaders();

        verifyNoMoreInteractions(mockEndPointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);
    }

    @Test
    public void testPreProcessEndPointSet_ShouldNotTrace() {
        when(mockEndPointSubmitter.getEndPoint()).thenReturn(mockEndPoint);
        mockShouldNotTraceHttpHeaders();

        assertNull(interceptor.preProcess(mockHttpRequest, null));

        final InOrder inOrder = inOrder(mockEndPointSubmitter, mockServerTracer);
        inOrder.verify(mockEndPointSubmitter).getEndPoint();
        inOrder.verify(mockServerTracer).setShouldTrace(false);
        inOrder.verify(mockServerTracer).clearCurrentSpan();

        verify(mockHttpRequest).getHttpHeaders();

        verifyNoMoreInteractions(mockEndPointSubmitter);
        verifyNoMoreInteractions(mockServerTracer);
        verifyNoMoreInteractions(mockHttpRequest);
        verifyNoMoreInteractions(mockHttpServletRequest);
    }

    private void mockHttpHeaders(final long traceid, final long spanId, final long parentSpanId) {
        final HttpHeaders mockHttpHeaders = mock(HttpHeaders.class);
        final MultivaluedMapImpl<String, String> multivaluedMapImpl = new MultivaluedMapImpl<String, String>();
        multivaluedMapImpl.add(HeaderConstants.TRACE_ID, String.valueOf(traceid));
        multivaluedMapImpl.add(HeaderConstants.SPAN_ID, String.valueOf(spanId));
        multivaluedMapImpl.add(HeaderConstants.PARENT_SPAN_ID, String.valueOf(parentSpanId));
        when(mockHttpHeaders.getRequestHeaders()).thenReturn(multivaluedMapImpl);
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
        multivaluedMapImpl.add(HeaderConstants.SHOULD_GET_TRACED, "false");
        when(mockHttpHeaders.getRequestHeaders()).thenReturn(multivaluedMapImpl);
        when(mockHttpRequest.getHttpHeaders()).thenReturn(mockHttpHeaders);
    }

}
