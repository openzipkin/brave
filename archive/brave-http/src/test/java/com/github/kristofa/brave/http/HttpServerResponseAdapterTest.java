package com.github.kristofa.brave.http;

import com.github.kristofa.brave.KeyValueAnnotation;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import zipkin.TraceKeys;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HttpServerResponseAdapterTest {

    private HttpServerResponseAdapter adapter;
    private HttpResponse response;

    @Before
    public void setup() {
        response = mock(HttpResponse.class);
        adapter = new HttpServerResponseAdapter(response);
    }

    @Test
    public void statusAnnotations() {
        when(response.getHttpStatusCode()).thenReturn(500);
        Collection<KeyValueAnnotation> annotations = adapter.responseAnnotations();
        assertEquals(1, annotations.size());
        KeyValueAnnotation a = annotations.iterator().next();
        assertEquals(TraceKeys.HTTP_STATUS_CODE, a.getKey());
        assertEquals("500", a.getValue());
    }
}
