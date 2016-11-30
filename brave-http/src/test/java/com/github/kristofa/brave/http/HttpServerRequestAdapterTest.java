package com.github.kristofa.brave.http;

import java.net.URI;
import java.util.Collection;

import com.github.kristofa.brave.KeyValueAnnotation;
import org.junit.Before;
import org.junit.Test;
import zipkin.TraceKeys;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpServerRequestAdapterTest {
    private HttpServerRequestAdapter adapter;
    private HttpServerRequest serverRequest;
    private SpanNameProvider spanNameProvider;

    @Before
    public void setup() {
        serverRequest = mock(HttpServerRequest.class);
        spanNameProvider = mock(SpanNameProvider.class);
        adapter = new HttpServerRequestAdapter(serverRequest, spanNameProvider);
    }

    @Test
    public void fullUriAnnotation() throws Exception {
        when(serverRequest.getUri()).thenReturn(new URI("http://youruri.com/a/b?myquery=you"));
        Collection<KeyValueAnnotation> annotations = adapter.requestAnnotations();
        assertEquals(1, annotations.size());
        KeyValueAnnotation a = annotations.iterator().next();
        assertEquals(TraceKeys.HTTP_URL, a.getKey());
        assertEquals("http://youruri.com/a/b?myquery=you", a.getValue());
    }
}
