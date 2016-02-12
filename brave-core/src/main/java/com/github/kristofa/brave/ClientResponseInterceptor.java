package com.github.kristofa.brave;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Contains logic for dealing with response from client request.
 * This means it will:
 *
 * - Submit potential annotations
 * - Submit client received annotation
 *
 * You will have to implement ClientResponseAdapter.
 *
 * @see ClientResponseAdapter
 */
public class ClientResponseInterceptor {

    private final ClientTracer clientTracer;

    public ClientResponseInterceptor(ClientTracer clientTracer) {
        this.clientTracer = checkNotNull(clientTracer, "Null clientTracer");
    }

    /**
     * Handle a client response.
     *
     * @param adapter Adapter that hides implementation details.
     */
    public void handle(ClientResponseAdapter adapter) {
        try {
            for (KeyValueAnnotation annotation : adapter.responseAnnotations()) {
                clientTracer.submitBinaryAnnotation(annotation.getKey(), annotation.getValue());
            }
        }
        finally
        {
            clientTracer.setClientReceived();
        }
    }
}
