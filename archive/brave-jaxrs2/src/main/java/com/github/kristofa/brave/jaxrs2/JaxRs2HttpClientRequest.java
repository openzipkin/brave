package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.http.HttpClientRequest;

import javax.ws.rs.client.ClientRequestContext;
import java.net.URI;


public class JaxRs2HttpClientRequest implements HttpClientRequest {

    private final ClientRequestContext clientRequestContext;

    public JaxRs2HttpClientRequest(final ClientRequestContext clientRequestContext) {
        this.clientRequestContext = clientRequestContext;
    }

    @Override
    public void addHeader(String header, String value) {
        clientRequestContext.getHeaders().add(header, value);
    }

    @Override
    public URI getUri() {
        return clientRequestContext.getUri();
    }

    @Override
    public String getHttpMethod() {
        return clientRequestContext.getMethod();
    }
}
