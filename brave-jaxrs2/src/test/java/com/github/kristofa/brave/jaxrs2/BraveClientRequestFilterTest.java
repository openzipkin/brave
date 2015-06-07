package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientTracer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.core.MultivaluedMap;

import java.net.URI;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BraveClientRequestFilterTest {

    public static final String GET = "GET";
    public static final String TEST_SPAN_NAME = "testSpanName";
    @Mock
    private ClientTracer clientTracer;

    @Mock
    private ClientRequestContext clientRequestContext;

    @Mock
    private MultivaluedMap<String, Object> headers;

    private BraveClientRequestFilter braveClientRequestFilter;

    private java.net.URI uri;

    @Before
    public void setUp() throws Exception {
        when(clientRequestContext.getHeaders()).thenReturn(headers);
        when(clientRequestContext.getMethod()).thenReturn(GET);
        uri = new URI("/test-uri");
        when(clientRequestContext.getUri()).thenReturn(uri);

        braveClientRequestFilter = new BraveClientRequestFilter(clientTracer, null);

    }

    @Test
    public void testFilter_haveHeader() throws Exception {
        when(headers.getFirst(BraveHttpHeaders.SpanName.getName())).thenReturn(TEST_SPAN_NAME);

        braveClientRequestFilter.filter(clientRequestContext);

        verify(clientTracer).startNewSpan(TEST_SPAN_NAME);
        verify(clientTracer).submitBinaryAnnotation("request", GET +" "+uri.getPath());
        verify(clientTracer).setClientSent();

        verify(clientRequestContext, times(2)).getHeaders();
        verify(clientRequestContext, times(3)).getUri();
        verify(clientRequestContext).getMethod();

        verify(headers).getFirst(BraveHttpHeaders.SpanName.getName());
        verify(headers).add(BraveHttpHeaders.Sampled.getName(), "false");

        verifyNoMoreInteractions(clientTracer);
        verifyNoMoreInteractions(clientRequestContext);
        verifyNoMoreInteractions(headers);
    }

    @Test
    public void testFilter_doNotHaveHeader() throws Exception {
        when(headers.getFirst(BraveHttpHeaders.SpanName.getName())).thenReturn(null);

        braveClientRequestFilter.filter(clientRequestContext);

        verify(clientTracer).startNewSpan(uri.getPath());
        verify(clientTracer).submitBinaryAnnotation("request", GET + " " + uri.getPath());
        verify(clientTracer).setClientSent();

        verify(clientRequestContext, times(2)).getHeaders();
        verify(clientRequestContext, times(3)).getUri();
        verify(clientRequestContext).getMethod();

        verify(headers).getFirst(BraveHttpHeaders.SpanName.getName());
        verify(headers).add(BraveHttpHeaders.Sampled.getName(), "false");

        verifyNoMoreInteractions(clientTracer);
        verifyNoMoreInteractions(clientRequestContext);
        verifyNoMoreInteractions(headers);
    }

}