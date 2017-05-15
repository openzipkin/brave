package com.github.kristofa.brave;

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
 * @deprecated Replaced by {@code HttpClientHander} from brave-instrumentation-http
 */
@Deprecated
public class ClientRequestInterceptor {

    private final ClientTracer clientTracer;

    public ClientRequestInterceptor(ClientTracer clientTracer) {
        this.clientTracer = checkNotNull(clientTracer, "Null clientTracer");
    }

    /**
     * Handles outgoing request.
     *
     * @param adapter The adapter deals with implementation specific details.
     */
    public void handle(ClientRequestAdapter adapter) {

        SpanId context = clientTracer.startNewSpan(adapter.getSpanName());
        if (context == null) {
            // We will not trace this request.
            adapter.addSpanIdToRequest(null);
        } else {
            adapter.addSpanIdToRequest(context);
            for (KeyValueAnnotation annotation : adapter.requestAnnotations()) {
                clientTracer.submitBinaryAnnotation(annotation.getKey(), annotation.getValue());
            }
            recordClientSentAnnotations(adapter.serverAddress());
        }
    }

    private void recordClientSentAnnotations(Endpoint serverAddress) {
        if (serverAddress == null) {
            clientTracer.setClientSent();
        } else {
            clientTracer.setClientSent(serverAddress);
        }
    }
}
