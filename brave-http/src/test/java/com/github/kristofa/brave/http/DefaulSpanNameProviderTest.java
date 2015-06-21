package com.github.kristofa.brave.http;


import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class DefaulSpanNameProviderTest {

    private final static String HTTP_METHOD = "POST";


    private DefaultSpanNameProvider spanNameProvider;
    private HttpClientRequest mockRequest;

    @Before
    public void setup() {
        mockRequest = mock(HttpClientRequest.class);
        spanNameProvider = new DefaultSpanNameProvider(mockRequest);
    }

    @Test
    public void getHttpMethod() {
        when(mockRequest.getHttpMethod()).thenReturn(HTTP_METHOD);
        assertEquals(HTTP_METHOD, spanNameProvider.spanName());
        verify(mockRequest).getHttpMethod();
        verifyNoMoreInteractions(mockRequest);
    }

}
