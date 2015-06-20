package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class ServerResponseInterceptorTest {

    private final static KeyValueAnnotation ANNOTATION1 = new KeyValueAnnotation("key1", "value1");
    private final static KeyValueAnnotation ANNOTATION2 = new KeyValueAnnotation("key2", "value2");

    private ServerResponseInterceptor interceptor;
    private ServerTracer serverTracer;
    private ServerResponseAdapter adapter;

    @Before
    public void setup() {
        serverTracer = mock(ServerTracer.class);
        interceptor = new ServerResponseInterceptor(serverTracer);
        adapter = mock(ServerResponseAdapter.class);
    }

    @Test
    public void testHandleNoCustomAnnotations() {

        when(adapter.responseAnnotations()).thenReturn(Collections.EMPTY_LIST);
        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer, adapter);
        inOrder.verify(adapter).responseAnnotations();
        inOrder.verify(serverTracer).setServerSend();
        inOrder.verify(serverTracer).clearCurrentSpan();
        verifyNoMoreInteractions(serverTracer);
    }

    @Test
    public void testHandleCustomAnnotations() {

        when(adapter.responseAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));
        interceptor.handle(adapter);
        InOrder inOrder = inOrder(serverTracer, adapter);
        inOrder.verify(adapter).responseAnnotations();
        inOrder.verify(serverTracer).submitBinaryAnnotation(ANNOTATION1.getKey(), ANNOTATION1.getValue());
        inOrder.verify(serverTracer).submitBinaryAnnotation(ANNOTATION2.getKey(), ANNOTATION2.getValue());
        inOrder.verify(serverTracer).setServerSend();
        inOrder.verify(serverTracer).clearCurrentSpan();
        verifyNoMoreInteractions(serverTracer);
    }
}
