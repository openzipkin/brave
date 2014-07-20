package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.client.ClientRequestInterceptor;
import com.github.kristofa.brave.client.ClientResponseInterceptor;
import com.google.common.base.Optional;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.apache.commons.lang.Validate;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This filter creates or forwards trace headers and sends cs and cr annotations.
 * Usage:
 * Client client = Client.create()
 * client.addFilter(new ClientTraceFilter(clientTracer));
 */
@Singleton
public class JerseyClientTraceFilter extends ClientFilter {

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final Optional<String> serviceName;

    @Inject
    public JerseyClientTraceFilter(ClientTracer clientTracer, Optional<String> serviceName) {
        Validate.notNull(clientTracer);
        Validate.notNull(serviceName);
        this.clientRequestInterceptor = new ClientRequestInterceptor(clientTracer);
        this.clientResponseInterceptor = new ClientResponseInterceptor(clientTracer);
        this.serviceName = serviceName;
    }

    @Override
    public ClientResponse handle(final ClientRequest clientRequest) throws ClientHandlerException {
        clientRequestInterceptor.handle(new JerseyClientRequestAdapter(clientRequest), serviceName);
        ClientResponse clientResponse = getNext().handle(clientRequest);
        clientResponseInterceptor.handle(new JerseyClientResponseAdapter(clientResponse));
        return clientResponse;
    }
}
