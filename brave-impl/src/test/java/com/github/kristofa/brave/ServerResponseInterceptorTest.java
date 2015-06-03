package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class ServerResponseInterceptorTest {

    private ServerResponseInterceptor interceptor;
    private ServerTracer serverTracer;

    @Before
    public void setup() {
        serverTracer = mock(ServerTracer.class);
        interceptor = new ServerResponseInterceptor(serverTracer);
    }

    @Test
    public void testHandle() {
        interceptor.handle();
        InOrder inOrder = inOrder(serverTracer);
        inOrder.verify(serverTracer).setServerSend();
        inOrder.verify(serverTracer).clearCurrentSpan();
        verifyNoMoreInteractions(serverTracer);
    }
}
