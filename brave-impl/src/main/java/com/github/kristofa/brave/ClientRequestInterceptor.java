package com.github.kristofa.brave;

/**
 * Contains logic for handling an outgoing client request.
 */
public class ClientRequestInterceptor {

    private final ClientTracer clientTracer;

    public ClientRequestInterceptor(ClientTracer clientTracer) {
        this.clientTracer = clientTracer;
    }

    /**
     * Handles outgoing request.
     *
     * @param adapter The adapter deals with implementation specific details.
     */
    public void handle(ClientRequestAdapter adapter) {

        SpanId spanId = clientTracer.startNewSpan(adapter.getSpanName());
        if (spanId == null) {
            // We will not trace this request.
            adapter.addSpanIdToRequest(null);
        } else {
            adapter.addSpanIdToRequest(spanId);
            clientTracer.setCurrentClientServiceName(adapter.getClientServiceName());
            for(KeyValueAnnotation annotation : adapter.requestAnnotations()) {
                clientTracer.submitBinaryAnnotation(annotation.getKey(), annotation.getValue());
            }
            clientTracer.setClientSent();
        }

    }

}
