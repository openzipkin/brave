package com.github.kristofa.brave;

import java.util.Objects;

/**
 * Contains logic for dealing with incoming response.
 *
 * @see ClientResponseAdapter
 */
public class ClientResponseInterceptor {

    private final ClientTracer clientTracer;

    public ClientResponseInterceptor(ClientTracer clientTracer) {
        this.clientTracer = Objects.requireNonNull(clientTracer);
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
