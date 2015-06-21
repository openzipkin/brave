package com.github.kristofa.brave.jersey;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import com.github.kristofa.brave.client.spanfilter.SpanNameFilter;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * This filter creates or forwards trace headers and sends cs and cr annotations. Usage: Client client = Client.create()
 * client.addFilter(new ClientTraceFilter(clientTracer));
 */
@Singleton
public class JerseyClientTraceFilter extends ClientFilter {

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final String serviceName;

    @Inject
    public JerseyClientTraceFilter(final ClientTracer clientTracer, final String serviceName) {
        this(clientTracer, serviceName, null);
    }

    public JerseyClientTraceFilter(final ClientTracer clientTracer, final String serviceName, final SpanNameFilter spanNameFilter) {
        checkNotNull(clientTracer, "Null clientTracer");
        clientRequestInterceptor = new ClientRequestInterceptor(clientTracer, spanNameFilter);
        clientResponseInterceptor = new ClientResponseInterceptor(clientTracer);
        this.serviceName = serviceName;
    }

    @Override
    public ClientResponse handle(final ClientRequest clientRequest) throws ClientHandlerException {
        clientRequestInterceptor.handle(new JerseyClientRequestAdapter(clientRequest), serviceName);
        final ClientResponse clientResponse = getNext().handle(clientRequest);
        clientResponseInterceptor.handle(new JerseyClientResponseAdapter(clientResponse));
        return clientResponse;
    }
}
