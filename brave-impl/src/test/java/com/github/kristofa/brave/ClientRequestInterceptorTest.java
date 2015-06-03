package com.github.kristofa.brave;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.mockito.Mockito.*;

public class ClientRequestInterceptorTest {

    private static final String SPAN_NAME = "getOrders";
    private static final String SERVICE_NAME = "orderService";
    private static final String REQUEST_REPRESENTATION = "/orders/user/4543";
    private ClientRequestInterceptor interceptor;
    private ClientTracer clientTracer;
    private ClientRequestAdapter adapter;

    @Before
    public void setup() {
        clientTracer = mock(ClientTracer.class);
        interceptor = new ClientRequestInterceptor(clientTracer);
        adapter = mock(ClientRequestAdapter.class);
    }

    @Test
    public void testNoSpanIdReturned() {
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(clientTracer.startNewSpan(SPAN_NAME)).thenReturn(null);
        interceptor.handle(adapter);
        InOrder inOrder = inOrder(clientTracer, adapter);
        inOrder.verify(adapter).getSpanName();
        inOrder.verify(clientTracer).startNewSpan(SPAN_NAME);
        inOrder.verify(adapter).addSpanIdToRequest(Optional.empty());
        verifyNoMoreInteractions(clientTracer, adapter);
    }

    @Test
    public void testSpanIdReturnedNoOptionalRequestRepresentation() {
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.getClientServiceName()).thenReturn(SERVICE_NAME);
        when(adapter.getRequestRepresentation()).thenReturn(Optional.empty());
        SpanId spanId = mock(SpanId.class);
        when(clientTracer.startNewSpan(SPAN_NAME)).thenReturn(spanId);
        interceptor.handle(adapter);

        InOrder inOrder = inOrder(clientTracer, adapter);
        inOrder.verify(adapter).getSpanName();
        inOrder.verify(clientTracer).startNewSpan(SPAN_NAME);
        inOrder.verify(adapter).addSpanIdToRequest(Optional.of(spanId));
        inOrder.verify(adapter).getClientServiceName();
        inOrder.verify(clientTracer).setCurrentClientServiceName(SERVICE_NAME);
        inOrder.verify(adapter).getRequestRepresentation();
        inOrder.verify(clientTracer).setClientSent();

        verifyNoMoreInteractions(clientTracer, adapter);
    }

    @Test
    public void testSpanIdReturnedRequestRepresentationProvided() {
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.getClientServiceName()).thenReturn(SERVICE_NAME);
        when(adapter.getRequestRepresentation()).thenReturn(Optional.of(REQUEST_REPRESENTATION));
        SpanId spanId = mock(SpanId.class);
        when(clientTracer.startNewSpan(SPAN_NAME)).thenReturn(spanId);
        interceptor.handle(adapter);

        InOrder inOrder = inOrder(clientTracer, adapter);
        inOrder.verify(adapter).getSpanName();
        inOrder.verify(clientTracer).startNewSpan(SPAN_NAME);
        inOrder.verify(adapter).addSpanIdToRequest(Optional.of(spanId));
        inOrder.verify(adapter).getClientServiceName();
        inOrder.verify(clientTracer).setCurrentClientServiceName(SERVICE_NAME);
        inOrder.verify(adapter, times(2)).getRequestRepresentation();
        inOrder.verify(clientTracer).submitBinaryAnnotation("request", REQUEST_REPRESENTATION);
        inOrder.verify(clientTracer).setClientSent();

        verifyNoMoreInteractions(clientTracer, adapter);
    }


}
