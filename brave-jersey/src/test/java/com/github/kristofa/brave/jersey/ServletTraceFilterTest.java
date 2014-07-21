package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ServletTraceFilterTest {

    private static final String CONTEXT_PATH = "TestContextPath";
    private static final String LOCAL_ADDR = "1.2.3.4";
    private static final int LOCAL_PORT = 4242;
    private static final long TRACE_ID = 1234;
    private static final long SPAN_ID = 2345;
    private static final long PARENT_SPAN_ID = 3456;
    private static final Boolean SAMPLED_TRUE = true;
    private static final String SPAN_NAME = "TestRequest";
    private static final Boolean SAMPLED_FALSE = false;

    @Mock
    ServerTracer serverTracer;
    @Mock
    EndPointSubmitter endPointSubmitter;
    @Mock
    HttpServletRequest servletRequest;
    @Mock
    HttpServletResponse servletResponse;
    @Mock
    FilterChain filterChain = mock(FilterChain.class);
    @InjectMocks
    ServletTraceFilter filter = new ServletTraceFilter(serverTracer, endPointSubmitter);

    @Test
    public void shouldClearSpanFirst() throws Exception {
        filter.doFilter(servletRequest, servletResponse, filterChain);

        verify(serverTracer).clearCurrentSpan();
    }

    @Test
    public void shouldCheckSubmitSpanState() throws Exception {
        filter.doFilter(servletRequest, servletResponse, filterChain);

        verify(endPointSubmitter).endPointSubmitted();
    }

    @Test
    public void shouldSubmitEndpointWithGivenPaths() throws Exception {
        when(endPointSubmitter.endPointSubmitted()).thenReturn(false);
        when(servletRequest.getContextPath()).thenReturn(CONTEXT_PATH);
        when(servletRequest.getLocalAddr()).thenReturn(LOCAL_ADDR);
        when(servletRequest.getLocalPort()).thenReturn(LOCAL_PORT);

        filter.doFilter(servletRequest, servletResponse, filterChain);

        verify(endPointSubmitter).submit(LOCAL_ADDR, LOCAL_PORT, CONTEXT_PATH);
    }

    @Test
    public void shouldGetTraceDataFromHeaders() throws Exception {
        when(endPointSubmitter.endPointSubmitted()).thenReturn(true);

        when(servletRequest.getHeader(BraveHttpHeaders.TraceId.getName())).thenReturn(Long.toString(TRACE_ID, 16));
        when(servletRequest.getHeader(BraveHttpHeaders.SpanId.getName())).thenReturn(Long.toString(SPAN_ID, 16));
        when(servletRequest.getHeader(BraveHttpHeaders.ParentSpanId.getName())).thenReturn(Long.toString(PARENT_SPAN_ID, 16));
        when(servletRequest.getHeader(BraveHttpHeaders.Sampled.getName())).thenReturn(String.valueOf(SAMPLED_TRUE));
        when(servletRequest.getHeader(BraveHttpHeaders.SpanName.getName())).thenReturn(SPAN_NAME);

        filter.doFilter(servletRequest, servletResponse, filterChain);

        verify(serverTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SPAN_NAME);
        verify(servletRequest).getHeader(BraveHttpHeaders.TraceId.getName());
        verify(servletRequest).getHeader(BraveHttpHeaders.SpanId.getName());
        verify(servletRequest).getHeader(BraveHttpHeaders.ParentSpanId.getName());
        verify(servletRequest).getHeader(BraveHttpHeaders.Sampled.getName());
        verify(servletRequest).getHeader(BraveHttpHeaders.SpanName.getName());
    }

    @Test
    public void shouldNotSubmitSpanWhenSampleIsFalse() throws Exception {
        when(endPointSubmitter.endPointSubmitted()).thenReturn(true);

        when(servletRequest.getHeader(BraveHttpHeaders.Sampled.getName())).thenReturn(String.valueOf(SAMPLED_FALSE));

        filter.doFilter(servletRequest, servletResponse, filterChain);

        final InOrder inOrder = inOrder(endPointSubmitter, serverTracer);
        inOrder.verify(serverTracer).clearCurrentSpan();
        inOrder.verify(endPointSubmitter).endPointSubmitted();
        inOrder.verify(serverTracer, never()).setStateCurrentTrace(anyLong(), anyLong(), anyLong(), anyString());
        inOrder.verify(serverTracer).setStateNoTracing();
    }
}
