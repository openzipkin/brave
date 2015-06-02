package com.github.kristofa.brave.jersey;

import java.net.URI;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.client.ClientRequestAdapter;
import com.google.common.base.Optional;
import com.sun.jersey.api.client.ClientRequest;

class JerseyClientRequestAdapter implements ClientRequestAdapter {

    private final ClientRequest clientRequest;

    JerseyClientRequestAdapter(final ClientRequest clientRequest) {
        this.clientRequest = clientRequest;
    }

    @Override
    public URI getUri() {
        return clientRequest.getURI();
    }

    @Override
    public String getMethod() {
        return clientRequest.getMethod();
    }

    @Override
    public Optional<String> getSpanName() {
        Optional<String> spanName = Optional.absent();
        final Object spanNameHeader = clientRequest.getHeaders().getFirst(BraveHttpHeaders.SpanName.getName());
        if (spanNameHeader != null) {
            spanName = Optional.fromNullable(spanNameHeader.toString());
        }
        return spanName;
    }

    @Override
    public void addHeader(final String header, final String value) {
        clientRequest.getHeaders().add(header, value);
    }
}
