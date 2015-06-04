package com.github.kristofa.brave.resteasy;

import java.net.URI;

import org.jboss.resteasy.client.ClientRequest;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.client.ClientRequestAdapter;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;

class RestEasyClientRequestAdapter implements ClientRequestAdapter {

    private final ClientRequest clientRequest;

    public RestEasyClientRequestAdapter(final ClientRequest clientRequest) {
        this.clientRequest = clientRequest;
    }

    @Override
    public URI getUri() {
        try {
            return URI.create(clientRequest.getUri());
        } catch (final Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String getMethod() {
        return clientRequest.getHttpMethod();
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
        clientRequest.header(header, value);
    }
}
