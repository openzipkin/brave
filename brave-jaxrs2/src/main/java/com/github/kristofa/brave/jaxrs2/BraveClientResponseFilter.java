package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * Intercepts JAX-RS 2 client responses and sends cr annotations. Also submits the completed span.
 */
@Provider
public class BraveClientResponseFilter implements ClientResponseFilter {

    private final ClientResponseInterceptor clientResponseInterceptor;

    @Inject
    public BraveClientResponseFilter(final ClientTracer clientTracer, final String serviceName) {
        Validate.notNull(clientTracer);
        clientResponseInterceptor = new ClientResponseInterceptor(clientTracer);
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) throws IOException {
        clientResponseInterceptor.handle(new JaxRS2ClientResponseAdapter(clientResponseContext));
    }
}
