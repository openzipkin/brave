package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.HttpResponse;
import javax.annotation.Priority;
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
@Priority(0)
public class BraveClientResponseFilter implements ClientResponseFilter {

    private final ClientResponseInterceptor responseInterceptor;

    @Inject
    public BraveClientResponseFilter(ClientResponseInterceptor responseInterceptor) {
        this.responseInterceptor = responseInterceptor;
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) throws IOException {

        final HttpResponse response = new JaxRs2HttpResponse(clientResponseContext);
        responseInterceptor.handle(new HttpClientResponseAdapter(response));
    }
}
