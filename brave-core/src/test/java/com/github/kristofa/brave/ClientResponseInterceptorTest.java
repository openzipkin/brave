package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class ClientResponseInterceptorTest {

    private ClientResponseInterceptor interceptor;
    private ClientTracer clientTracer;
    private ClientResponseAdapter adapter;

    @Before
    public void setup() {
        clientTracer = mock(ClientTracer.class);
        interceptor = new ClientResponseInterceptor(clientTracer);
        adapter = mock(ClientResponseAdapter.class);
    }

    @Test
    public void testNoAnnotationsProvided() {
        when(adapter.responseAnnotations()).thenReturn(Collections.EMPTY_LIST);
        interceptor.handle(adapter);
        InOrder inOrder = inOrder(clientTracer, adapter);
        inOrder.verify(adapter).responseAnnotations();
        inOrder.verify(clientTracer).setClientReceived();
        verifyNoMoreInteractions(clientTracer, adapter);
    }

    @Test
    public void testAnnotationsProvided() {
        KeyValueAnnotation a1 = KeyValueAnnotation.create("key1", "value1");
        KeyValueAnnotation a2 = KeyValueAnnotation.create("key2", "value2");

        when(adapter.responseAnnotations()).thenReturn(Arrays.asList(a1, a2));
        interceptor.handle(adapter);
        InOrder inOrder = inOrder(clientTracer, adapter);
        inOrder.verify(adapter).responseAnnotations();
        inOrder.verify(clientTracer).submitBinaryAnnotation(a1.getKey(), a1.getValue());
        inOrder.verify(clientTracer).submitBinaryAnnotation(a2.getKey(), a2.getValue());
        inOrder.verify(clientTracer).setClientReceived();
        verifyNoMoreInteractions(clientTracer, adapter);
    }
}
