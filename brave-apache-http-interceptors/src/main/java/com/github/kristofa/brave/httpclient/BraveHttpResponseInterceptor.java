package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import org.apache.commons.lang3.Validate;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * Apache HttpClient {@link HttpResponseInterceptor} that gets the HttpResponse, inspects the state. If the response
 * indicates an error it submits error code and failure annotation. Finally it submits the client received annotation.
 * 
 * @author kristof
 */
public class BraveHttpResponseInterceptor implements HttpResponseInterceptor {

    private final ClientResponseInterceptor traceResponseBuilder;

    /**
     * Create a new instance.
     * 
     * @param clientTracer ClientTracer. Should not be <code>null</code>.
     */
    public BraveHttpResponseInterceptor(final ClientTracer clientTracer) {
        Validate.notNull(clientTracer);
        this.traceResponseBuilder = new ClientResponseInterceptor(clientTracer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(final HttpResponse response, final HttpContext context) throws HttpException, IOException {
        traceResponseBuilder.handle(new ApacheClientResponseAdapter(response));
    }

}
