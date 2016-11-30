package com.github.kristofa.brave.http;

import com.github.kristofa.brave.KeyValueAnnotation;
import org.junit.Before;
import org.junit.Test;
import zipkin.TraceKeys;

import java.net.URI;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class HttpClientRequestAdapterTest {

    private static final String SPAN_NAME = "span_name";
    private static final String TEST_URI = "http://abc.com/request";

    private HttpClientRequestAdapter clientRequestAdapter;
    private HttpClientRequest request;
    private SpanNameProvider spanNameProvider;

    @Before
    public void setup() {
        request = mock(HttpClientRequest.class);
        spanNameProvider = mock(SpanNameProvider.class);
        clientRequestAdapter = new HttpClientRequestAdapter(request, spanNameProvider);
    }

    @Test
    public void getSpanName() {
        when(spanNameProvider.spanName(request)).thenReturn(SPAN_NAME);
        assertEquals(SPAN_NAME, clientRequestAdapter.getSpanName());
        verify(spanNameProvider).spanName(request);
        verifyNoMoreInteractions(request, spanNameProvider);
    }

    @Test
    public void requestAnnotations() {
        when(request.getUri()).thenReturn(URI.create(TEST_URI));
        Collection<KeyValueAnnotation> annotations = clientRequestAdapter.requestAnnotations();
        assertEquals(1, annotations.size());
        KeyValueAnnotation a = annotations.iterator().next();
        assertEquals(TraceKeys.HTTP_URL, a.getKey());
        assertEquals(TEST_URI, a.getValue());
        verify(request).getUri();
        verifyNoMoreInteractions(request, spanNameProvider);
    }
}
