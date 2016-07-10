package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

public class ClientRequestInterceptorTest {

    private static final String SPAN_NAME = "getOrders";
    private static final String SERVICE_NAME = "orderService";
    private static final int TARGET_IP = 192 << 24 | 168 << 16 | 1;
    private static final int TARGET_PORT = 80;
    private static final KeyValueAnnotation ANNOTATION1 = KeyValueAnnotation.create(zipkin.TraceKeys.HTTP_URL, "/orders/user/4543");
    private static final KeyValueAnnotation ANNOTATION2 = KeyValueAnnotation.create("http.code", "200");
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
        inOrder.verify(adapter).addSpanIdToRequest(null);
        verifyNoMoreInteractions(clientTracer, adapter);
    }

    @Test
    public void testSpanIdReturnedNoAnnotationsProvided() {
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Collections.EMPTY_LIST);
        SpanId spanId = SpanId.builder().spanId(1L).build();
        when(clientTracer.startNewSpan(SPAN_NAME)).thenReturn(spanId);
        interceptor.handle(adapter);

        InOrder inOrder = inOrder(clientTracer, adapter);
        inOrder.verify(adapter).getSpanName();
        inOrder.verify(clientTracer).startNewSpan(SPAN_NAME);
        inOrder.verify(adapter).addSpanIdToRequest(spanId);
        inOrder.verify(adapter).requestAnnotations();
        inOrder.verify(adapter).serverAddress();
        inOrder.verify(clientTracer).setClientSent();

        verifyNoMoreInteractions(clientTracer, adapter);
    }

    @Test
    public void testSpanIdReturnedAnnotationsProvided() {
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Arrays.asList(ANNOTATION1, ANNOTATION2));
        SpanId spanId = SpanId.builder().spanId(1L).build();
        when(clientTracer.startNewSpan(SPAN_NAME)).thenReturn(spanId);
        interceptor.handle(adapter);

        InOrder inOrder = inOrder(clientTracer, adapter);
        inOrder.verify(adapter).getSpanName();
        inOrder.verify(clientTracer).startNewSpan(SPAN_NAME);
        inOrder.verify(adapter).addSpanIdToRequest(spanId);
        inOrder.verify(adapter).requestAnnotations();
        inOrder.verify(clientTracer).submitBinaryAnnotation(ANNOTATION1.getKey(), ANNOTATION1.getValue());
        inOrder.verify(clientTracer).submitBinaryAnnotation(ANNOTATION2.getKey(), ANNOTATION2.getValue());
        inOrder.verify(adapter).serverAddress();
        inOrder.verify(clientTracer).setClientSent();

        verifyNoMoreInteractions(clientTracer, adapter);
    }

    @Test
    public void testServerAddressAdded() {
        when(adapter.getSpanName()).thenReturn(SPAN_NAME);
        when(adapter.requestAnnotations()).thenReturn(Collections.EMPTY_LIST);
        when(adapter.serverAddress()).thenReturn(Endpoint.create(SERVICE_NAME, TARGET_IP, TARGET_PORT));
        SpanId spanId = SpanId.builder().spanId(1L).build();
        when(clientTracer.startNewSpan(SPAN_NAME)).thenReturn(spanId);
        interceptor.handle(adapter);

        InOrder inOrder = inOrder(clientTracer, adapter);
        inOrder.verify(adapter).getSpanName();
        inOrder.verify(clientTracer).startNewSpan(SPAN_NAME);
        inOrder.verify(adapter).addSpanIdToRequest(spanId);
        inOrder.verify(adapter).requestAnnotations();
        inOrder.verify(adapter).serverAddress();
        inOrder.verify(clientTracer).setClientSent(TARGET_IP, TARGET_PORT, SERVICE_NAME.toLowerCase());
        verifyNoMoreInteractions(clientTracer, adapter);
    }

}
