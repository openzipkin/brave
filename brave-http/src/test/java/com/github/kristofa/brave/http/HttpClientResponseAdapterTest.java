package com.github.kristofa.brave.http;


import com.github.kristofa.brave.KeyValueAnnotation;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class HttpClientResponseAdapterTest {

    private HttpClientResponseAdapter adapter;
    private HttpResponse response;

    @Before
    public void setup() {
        response = mock(HttpResponse.class);
        adapter = new HttpClientResponseAdapter(response);
    }

    @Test
    public void successResponse() {
        when(response.getHttpStatusCode()).thenReturn(200);
        assertTrue(adapter.responseAnnotations().isEmpty());
        verify(response).getHttpStatusCode();
        verifyNoMoreInteractions(response);
    }

    @Test
    public void nonSuccessResponse() {
        when(response.getHttpStatusCode()).thenReturn(500);
        Collection<KeyValueAnnotation> annotations = adapter.responseAnnotations();
        assertEquals(1, annotations.size());
        KeyValueAnnotation a = annotations.iterator().next();
        assertEquals("http.responsecode", a.getKey());
        assertEquals("500", a.getValue());
    }
}
