package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.EndPointSubmitter;
import com.github.kristofa.brave.ServerTracer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BraveContainerRequestFilterTest {

    private static final String CONTEXT_PATH = "TestContextPath";
    private static final String LOCAL_ADDR = "1.2.3.4";
    private static final int LOCAL_PORT = 4242;
    private static final long TRACE_ID = 1234;
    private static final long SPAN_ID = 2345;
    private static final long PARENT_SPAN_ID = 3456;
    private static final String SPAN_NAME = "TestRequest";

    @Mock
    private ServerTracer serverTracer;

    @Mock
    private EndPointSubmitter endPointSubmitter;

    @Mock
    private ContainerRequestContext containerRequestContext;

    @InjectMocks
    private BraveContainerRequestFilter containerRequestFilter = new BraveContainerRequestFilter(serverTracer, endPointSubmitter);

    @Before
    public void setUp() throws URISyntaxException {
        URI baseUri = new URI("http://" + LOCAL_ADDR + ":" + LOCAL_PORT + "/" + CONTEXT_PATH);

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUri()).thenReturn(baseUri);
        when(uriInfo.getAbsolutePath()).thenReturn(baseUri);

        when(containerRequestContext.getUriInfo()).thenReturn(uriInfo);
    }

    @Test
    public void testClearSpan() throws IOException {
        when(endPointSubmitter.endPointSubmitted()).thenReturn(true);
        containerRequestFilter.filter(containerRequestContext);

        verify(serverTracer).clearCurrentSpan();
    }

    @Test
    public void testEndpointSubmitted() throws IOException {
        when(endPointSubmitter.endPointSubmitted()).thenReturn(false);
        containerRequestFilter.filter(containerRequestContext);

        verify(endPointSubmitter).submit(LOCAL_ADDR, LOCAL_PORT, CONTEXT_PATH);
    }

    @Test
    public void testNoTraceData() throws IOException {
        when(endPointSubmitter.endPointSubmitted()).thenReturn(true);
        containerRequestFilter.filter(containerRequestContext);

        verify(serverTracer).setStateUnknown("/" + CONTEXT_PATH);
    }

    @Test
    public void testTracingDisabled() throws IOException {
        when(endPointSubmitter.endPointSubmitted()).thenReturn(true);
        when(containerRequestContext.getHeaderString(BraveHttpHeaders.Sampled.getName())).thenReturn(String.valueOf(false));

        containerRequestFilter.filter(containerRequestContext);

        verify(serverTracer).setStateNoTracing();
    }

    @Test
    public void testTraceData() throws IOException {
        when(endPointSubmitter.endPointSubmitted()).thenReturn(true);

        when(containerRequestContext.getHeaderString(BraveHttpHeaders.TraceId.getName())).thenReturn(Long.toString(TRACE_ID, 16));
        when(containerRequestContext.getHeaderString(BraveHttpHeaders.SpanId.getName())).thenReturn(Long.toString(SPAN_ID, 16));
        when(containerRequestContext.getHeaderString(BraveHttpHeaders.ParentSpanId.getName())).thenReturn(Long.toString(PARENT_SPAN_ID, 16));
        when(containerRequestContext.getHeaderString(BraveHttpHeaders.Sampled.getName())).thenReturn(String.valueOf(true));
        when(containerRequestContext.getHeaderString(BraveHttpHeaders.SpanName.getName())).thenReturn(SPAN_NAME);

        containerRequestFilter.filter(containerRequestContext);

        verify(containerRequestContext).getHeaderString(BraveHttpHeaders.TraceId.getName());
        verify(containerRequestContext).getHeaderString(BraveHttpHeaders.SpanId.getName());
        verify(containerRequestContext).getHeaderString(BraveHttpHeaders.ParentSpanId.getName());
        verify(containerRequestContext).getHeaderString(BraveHttpHeaders.Sampled.getName());
        verify(containerRequestContext).getHeaderString(BraveHttpHeaders.SpanName.getName());

        verify(serverTracer).setStateCurrentTrace(TRACE_ID, SPAN_ID, PARENT_SPAN_ID, SPAN_NAME);
    }

}
