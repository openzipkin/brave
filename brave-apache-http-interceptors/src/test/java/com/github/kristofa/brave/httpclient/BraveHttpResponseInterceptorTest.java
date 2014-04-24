package com.github.kristofa.brave.httpclient;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import com.github.kristofa.brave.ClientTracer;

public class BraveHttpResponseInterceptorTest {

    private ClientTracer clientTracer;
    private BraveHttpResponseInterceptor responseInterceptor;
    private HttpResponse response;
    private StatusLine mockStatusLine;

    @Before
    public void setUp() throws Exception {
        clientTracer = mock(ClientTracer.class);
        responseInterceptor = new BraveHttpResponseInterceptor(clientTracer);
        response = mock(HttpResponse.class);
        mockStatusLine = mock(StatusLine.class);
        when(response.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(200);
    }

    @Test
    public void testProcess() throws HttpException, IOException {

        when(mockStatusLine.getStatusCode()).thenReturn(200);
        responseInterceptor.process(response, null);
        verify(clientTracer).setClientReceived();
        verifyNoMoreInteractions(clientTracer);
    }

    @Test
    public void testProcessErrorResponseCodeSmallerThan200() throws HttpException, IOException {

        when(mockStatusLine.getStatusCode()).thenReturn(199);
        responseInterceptor.process(response, null);

        final InOrder inOrder = inOrder(clientTracer);

        inOrder.verify(clientTracer).submitBinaryAnnotation("http.responsecode", 199);
        inOrder.verify(clientTracer).submitAnnotation("failure");
        inOrder.verify(clientTracer).setClientReceived();
        verifyNoMoreInteractions(clientTracer);
    }

    @Test
    public void testProcessErrorResponseCodeBiggerThan299() throws HttpException, IOException {

        when(mockStatusLine.getStatusCode()).thenReturn(300);
        responseInterceptor.process(response, null);

        final InOrder inOrder = inOrder(clientTracer);

        inOrder.verify(clientTracer).submitBinaryAnnotation("http.responsecode", 300);
        inOrder.verify(clientTracer).submitAnnotation("failure");
        inOrder.verify(clientTracer).setClientReceived();
        verifyNoMoreInteractions(clientTracer);
    }

}
