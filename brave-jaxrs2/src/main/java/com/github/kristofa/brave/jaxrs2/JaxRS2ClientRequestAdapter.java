package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.BraveHttpHeaders;
import com.github.kristofa.brave.client.ClientRequestAdapter;

import javax.ws.rs.client.ClientRequestContext;
import java.net.URI;

public class JaxRS2ClientRequestAdapter implements ClientRequestAdapter {

    private final ClientRequestContext clientRequest;

    JaxRS2ClientRequestAdapter(final ClientRequestContext clientRequestContext) {
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
    public String getSpanName() {
        final Object spanNameHeader = clientRequest.getHeaders().getFirst(BraveHttpHeaders.SpanName.getName());
        return spanNameHeader != null ? spanNameHeader.toString() : null;
    }

    @Override
    public void addHeader(final String header, final String value) {
        clientRequest.getHeaders().add(header, value);
    }
}
