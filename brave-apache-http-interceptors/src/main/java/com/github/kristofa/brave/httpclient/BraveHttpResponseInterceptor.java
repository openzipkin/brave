package com.github.kristofa.brave.httpclient;

import java.io.IOException;

import org.apache.commons.lang.Validate;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

import com.github.kristofa.brave.ClientTracer;

/**
 * Apache HttpClient {@link HttpResponseInterceptor} that gets the HttpResponse, inspects the state. If the response
 * indicates an error it submits error code and failure annotation. Finally it submits the client received annotation.
 * 
 * @author kristof
 */
public class BraveHttpResponseInterceptor implements HttpResponseInterceptor {

    private static final String FAILURE_ANNOTATION = "failure";
    private static final String HTTP_RESPONSE_CODE_ANNOTATION = "http.responsecode";

    private final ClientTracer clientTracer;

    /**
     * Create a new instance.
     * 
     * @param clientTracer ClientTracer. Should not be <code>null</code>.
     */
    public BraveHttpResponseInterceptor(final ClientTracer clientTracer) {
        Validate.notNull(clientTracer);
        this.clientTracer = clientTracer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        try {

            final int responseStatus = response.getStatusLine().getStatusCode();
            if (responseStatus < 200 || responseStatus > 299) {
                // In this case response will be the error message.
                clientTracer.submitBinaryAnnotation(HTTP_RESPONSE_CODE_ANNOTATION, responseStatus);
                clientTracer.submitAnnotation(FAILURE_ANNOTATION);
            }
        } finally {
            clientTracer.setClientReceived();
        }

    }

}
