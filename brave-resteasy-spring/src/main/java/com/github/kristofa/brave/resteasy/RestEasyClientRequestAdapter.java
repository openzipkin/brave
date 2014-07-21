package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import org.jboss.resteasy.client.ClientRequest;

import java.net.URI;

public class RestEasyClientRequestAdapter implements ClientRequestAdapter {
    private final ClientRequest clientRequest;

    public RestEasyClientRequestAdapter(ClientRequest clientRequest) {
        this.clientRequest = clientRequest;
    }

    @Override
    public URI getUri() {
        try {
            return URI.create(clientRequest.getUri());
        } catch (Exception e) {
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
        Object spanNameHeader = clientRequest.getHeaders().getFirst(BraveHttpHeaders.SpanName.getName());
        if (spanNameHeader != null) {
            spanName = Optional.fromNullable(spanNameHeader.toString());
        }
        return spanName;
    }

    @Override
    public void addHeader(String header, String value) {
        clientRequest.header(header, value);
    }
}
