package com.github.kristofa.brave.jersey;


import com.github.kristofa.brave.http.HttpClientRequest;
import com.sun.jersey.api.client.ClientRequest;

import java.net.URI;

public class JerseyHttpRequest implements HttpClientRequest {

    private final ClientRequest clientRequest;

    public JerseyHttpRequest(ClientRequest clientRequest) {
        this.clientRequest = clientRequest;
    }

    @Override
    public void addHeader(String header, String value) {
        clientRequest.getHeaders().add(header, value);
    }

    @Override
    public URI getUri() {
        return clientRequest.getURI();
    }

    @Override
    public String getHttpMethod() {
        return clientRequest.getMethod();
    }
}
