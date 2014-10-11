package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.google.common.base.Optional;

import javax.ws.rs.client.ClientRequestContext;
import java.net.URI;

public class JerseyClientRequestAdapter implements ClientRequestAdapter {

    private final ClientRequestContext clientRequest;

    JerseyClientRequestAdapter(final ClientRequestContext clientRequestContext) {
        this.clientRequest = clientRequestContext;
    }

    @Override
    public URI getUri() {
        return clientRequest.getUri();
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
