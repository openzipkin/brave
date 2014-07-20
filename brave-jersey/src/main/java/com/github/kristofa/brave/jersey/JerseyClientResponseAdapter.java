package com.github.kristofa.brave.jersey;

import com.github.kristofa.brave.ClientResponseAdapter;
import com.sun.jersey.api.client.ClientResponse;

public class JerseyClientResponseAdapter implements ClientResponseAdapter {
    private final ClientResponse response;

    public JerseyClientResponseAdapter(ClientResponse response) {
        this.response = response;
    }

    @Override
    public int getStatusCode() {
        return response.getStatus();
    }
}
