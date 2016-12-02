package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.twitter.zipkin.gen.Endpoint;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Contains logic for handling an outgoing client request.
 * This means it will:
 *
 * - Start a new span
 * - Make sure span parameters are added to outgoing request
 * - Submit client sent annotation
 *
 *
 * The only thing you have to do is implement a ClientRequestAdapter.
 *
 * @see ClientRequestAdapter
 *
 */
public class ClientRequestInterceptor {

    private final ClientTracer clientTracer;

    public ClientRequestInterceptor(ClientTracer clientTracer) {
        this.clientTracer = checkNotNull(clientTracer, "Null clientTracer");
    }

    @Nullable
    public SpanId internalStartSpan(ClientRequestAdapter adapter) {
        SpanId spanId = clientTracer.startNewSpan(adapter.getSpanName());
        if (spanId != null) {
            for (KeyValueAnnotation annotation : adapter.requestAnnotations()) {
                clientTracer.submitBinaryAnnotation(annotation.getKey(), annotation.getValue());
            }
            recordClientSentAnnotations(adapter.serverAddress());
        }
        return spanId;
    }

    /**
     * @deprecated will transition to {@link #internalStartSpan} or similar.
     */
    @Deprecated
    public void handle(ClientRequestAdapter adapter) {
        SpanId spanId = internalStartSpan(adapter);
        // using the deprecated method to ensure 3rd party adapters still work.
        adapter.addSpanIdToRequest(spanId);
    }

    private void recordClientSentAnnotations(Endpoint serverAddress) {
        if (serverAddress == null) {
            clientTracer.setClientSent();
        } else {
            clientTracer.setClientSent(serverAddress);
        }
    }
}