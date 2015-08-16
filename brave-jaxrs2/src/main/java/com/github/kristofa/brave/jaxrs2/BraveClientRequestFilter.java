package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.http.HttpClientRequest;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.ServiceNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;

import javax.inject.Inject;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * Intercepts JAX-RS 2 client requests and adds or forwards tracing information in the header.
 * Also sends cs annotations.
 */
@Provider
public class BraveClientRequestFilter implements ClientRequestFilter {

    private final ServiceNameProvider serviceNameProvider;
    private final ClientRequestInterceptor requestInterceptor;
    private final SpanNameProvider spanNameProvider;

    @Inject
    public BraveClientRequestFilter(ServiceNameProvider serviceNameProvider, SpanNameProvider spanNameProvider, ClientRequestInterceptor requestInterceptor) {
        this.requestInterceptor = requestInterceptor;
        this.serviceNameProvider = serviceNameProvider;
        this.spanNameProvider = spanNameProvider;
    }


    @Override
    public void filter(ClientRequestContext clientRequestContext) throws IOException {
        final HttpClientRequest req = new JaxRs2HttpClientRequest(clientRequestContext);
        requestInterceptor.handle(new HttpClientRequestAdapter(req, serviceNameProvider, spanNameProvider));
    }
}
