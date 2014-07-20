package com.github.kristofa.brave.client;

import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientTracer;
import org.apache.commons.lang.Validate;

public class ClientResponseInterceptor {
    private static final String FAILURE_ANNOTATION = "failure";
    private static final String HTTP_RESPONSE_CODE_ANNOTATION = "http.responsecode";

    private final ClientTracer clientTracer;

    public ClientResponseInterceptor(ClientTracer clientTracer) {
        Validate.notNull(clientTracer);
        this.clientTracer = clientTracer;
    }

    public void handle(ClientResponseAdapter clientResponseAdapter) {
        try {
            int responseStatus = clientResponseAdapter.getStatusCode();
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
