package com.github.kristofa.brave.jersey2;

import com.github.kristofa.brave.ClientResponseAdapter;

import javax.ws.rs.client.ClientResponseContext;

public class JerseyClientResponseAdapter implements ClientResponseAdapter {

    private final ClientResponseContext response;

    public JerseyClientResponseAdapter(final ClientResponseContext response) {
        this.response = response;
    }

    @Override
    public int getStatusCode() {
        return response.getStatus();
    }
}
