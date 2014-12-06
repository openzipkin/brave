package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;
import com.google.common.base.Optional;
import org.apache.commons.lang.Validate;

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

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final Optional<String> serviceName;

    @Inject
    public BraveClientRequestFilter(final ClientTracer clientTracer, final Optional<String> serviceName) {
        this(clientTracer, serviceName, Optional.<SpanNameFilter>absent());
    }

    public BraveClientRequestFilter(final ClientTracer clientTracer, final Optional<String> serviceName, final Optional<SpanNameFilter> spanNameFilter) {
        Validate.notNull(clientTracer);
        Validate.notNull(serviceName);
        Validate.notNull(spanNameFilter);
        clientRequestInterceptor = new ClientRequestInterceptor(clientTracer, spanNameFilter);
        this.serviceName = serviceName;
    }

    @Override
    public void filter(ClientRequestContext clientRequestContext) throws IOException {
        clientRequestInterceptor.handle(new JaxRS2ClientRequestAdapter(clientRequestContext), serviceName);
    }
}
