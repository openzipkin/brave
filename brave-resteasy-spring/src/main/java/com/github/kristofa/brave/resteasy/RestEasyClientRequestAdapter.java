package com.github.kristofa.brave.resteasy;

import java.net.URI;

import org.jboss.resteasy.client.ClientRequest;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.client.ClientRequestAdapter;

class RestEasyClientRequestAdapter implements ClientRequestAdapter {

    private final ClientRequest clientRequest;

    public RestEasyClientRequestAdapter(final ClientRequest clientRequest) {
        this.clientRequest = clientRequest;
    }

    @Override
    public URI getUri() {
        try {
            return URI.create(clientRequest.getUri());
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getMethod() {
        return clientRequest.getHttpMethod();
    }

    @Override
    public String getSpanName() {
        final Object spanNameHeader = clientRequest.getHeaders().getFirst(BraveHttpHeaders.SpanName.getName());
        return spanNameHeader != null ? spanNameHeader.toString() : null;
    }

    @Override
    public void addHeader(final String header, final String value) {
        clientRequest.header(header, value);
    }
}
