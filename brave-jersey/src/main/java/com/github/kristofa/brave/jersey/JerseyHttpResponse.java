package com.github.kristofa.brave.jersey;


import com.github.kristofa.brave.http.HttpResponse;
import com.sun.jersey.api.client.ClientResponse;

public class JerseyHttpResponse implements HttpResponse {

    private final ClientResponse clientResponse;

    public JerseyHttpResponse(ClientResponse clientResponse) {
        this.clientResponse = clientResponse;
    }

    @Override
    public int getHttpStatusCode() {
        return clientResponse.getStatus();
    }
}
