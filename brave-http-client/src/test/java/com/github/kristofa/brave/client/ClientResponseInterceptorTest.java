package com.github.kristofa.brave.client;

import com.github.kristofa.brave.ClientTracer;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ClientResponseInterceptorTest {
    private static final int SUCCESS_CODE = 200;
    private static final int FAILURE_CODE_UNDER = 199;
    private static final int FAILURE_CODE_OVER = 300;

    private ClientResponseInterceptor interceptor;
    private ClientTracer clientTracer;
    private ClientResponseAdapter clientResponseAdapter;

    @Before
    public void setUp() throws Exception {
        clientTracer = mock(ClientTracer.class);
        interceptor = new ClientResponseInterceptor(clientTracer);
        clientResponseAdapter = mock(ClientResponseAdapter.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBraveHttpRequestInterceptor() {
        new ClientResponseInterceptor(null);
    }

    @Test
    public void testHandleSuccess() {
        when(clientResponseAdapter.getStatusCode()).thenReturn(SUCCESS_CODE);

        interceptor.handle(clientResponseAdapter);

        final InOrder inOrder = inOrder(clientTracer);
        inOrder.verify(clientTracer).submitBinaryAnnotation("http.responsecode", SUCCESS_CODE);
        inOrder.verify(clientTracer).setClientReceived();
        verifyNoMoreInteractions(clientTracer);
    }

    @Test
    public void testHandleFailureUnder200() {
        when(clientResponseAdapter.getStatusCode()).thenReturn(FAILURE_CODE_UNDER);

        interceptor.handle(clientResponseAdapter);

        final InOrder inOrder = inOrder(clientTracer);
        inOrder.verify(clientTracer).submitBinaryAnnotation("http.responsecode", FAILURE_CODE_UNDER);
        inOrder.verify(clientTracer).submitAnnotation("failure");
        inOrder.verify(clientTracer).setClientReceived();
        verifyNoMoreInteractions(clientTracer);
    }

    @Test
    public void testHandleFailureOver299() {
        when(clientResponseAdapter.getStatusCode()).thenReturn(FAILURE_CODE_OVER);

        interceptor.handle(clientResponseAdapter);

        final InOrder inOrder = inOrder(clientTracer);
        inOrder.verify(clientTracer).submitBinaryAnnotation("http.responsecode", FAILURE_CODE_OVER);
        inOrder.verify(clientTracer).submitAnnotation("failure");
        inOrder.verify(clientTracer).setClientReceived();
        verifyNoMoreInteractions(clientTracer);
    }
}
