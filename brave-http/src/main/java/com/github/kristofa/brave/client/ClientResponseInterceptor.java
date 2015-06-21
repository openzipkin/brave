package com.github.kristofa.brave.client;

import com.github.kristofa.brave.ClientTracer;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Intercepts a response of a Client Request. Abstraction on top of {@link ClientTracer}. Will submit CR annotation. Used
 * together with {@link ClientRequestInterceptor}. It is advised to use {@link ClientRequestInterceptor} and
 * {@link ClientResponseInterceptor} to integrate a library with brave as opposed to use ClientTracer directly.
 * 
 * @see ClientResponseAdapter
 * @see ClientRequestInterceptor
 */
public class ClientResponseInterceptor {

    private static final String FAILURE_ANNOTATION = "failure";
    private static final String HTTP_RESPONSE_CODE_ANNOTATION = "http.responsecode";

    private final ClientTracer clientTracer;

    public ClientResponseInterceptor(final ClientTracer clientTracer) {
        this.clientTracer = checkNotNull(clientTracer, "Null clientTracer");
    }

    /**
     * Handles a Client Response.
     * 
     * @param clientResponseAdapter
     */
    public void handle(final ClientResponseAdapter clientResponseAdapter) {
        try {
            final int responseStatus = clientResponseAdapter.getStatusCode();
            clientTracer.submitBinaryAnnotation(HTTP_RESPONSE_CODE_ANNOTATION, responseStatus);
            if (responseStatus < 200 || responseStatus > 299) {
                // In this case response will be the error message.
                clientTracer.submitAnnotation(FAILURE_ANNOTATION);
            }
        } finally {
            clientTracer.setClientReceived();
        }
    }
}
