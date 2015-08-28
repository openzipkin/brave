package com.github.kristofa.brave.jersey;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.github.kristofa.brave.http.ServiceNameProvider;
import com.github.kristofa.brave.http.SpanNameProvider;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;


/**
 * This filter creates or forwards trace headers and sends cs and cr annotations. Usage:
 *
 * <pre>
 * Client client = Client.create()
 * client.addFilter(new ClientTraceFilter(clientTracer));
 * </pre>
 */
@Singleton
public class JerseyClientTraceFilter extends ClientFilter {

    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final ServiceNameProvider serviceNameProvider;
    private final SpanNameProvider spanNameProvider;

    @Inject
    public JerseyClientTraceFilter(ServiceNameProvider serviceNameProvider, SpanNameProvider spanNameProvider, ClientRequestInterceptor requestInterceptor, ClientResponseInterceptor responseInterceptor) {
        this.serviceNameProvider = serviceNameProvider;
        this.spanNameProvider = spanNameProvider;
        this.clientRequestInterceptor = requestInterceptor;
        this.clientResponseInterceptor = responseInterceptor;
    }

    @Override
    public ClientResponse handle(final ClientRequest clientRequest) throws ClientHandlerException {

        clientRequestInterceptor.handle(new HttpClientRequestAdapter(new JerseyHttpRequest(clientRequest), serviceNameProvider, spanNameProvider));
        final ClientResponse clientResponse = getNext().handle(clientRequest);
        clientResponseInterceptor.handle(new HttpClientResponseAdapter(new JerseyHttpResponse(clientResponse)));
        return clientResponse;
    }
}
