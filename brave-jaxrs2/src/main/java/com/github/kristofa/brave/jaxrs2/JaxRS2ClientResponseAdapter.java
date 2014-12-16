package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.ClientResponseAdapter;

import javax.ws.rs.client.ClientResponseContext;

public class JaxRS2ClientResponseAdapter implements ClientResponseAdapter {

    private final ClientResponseContext response;

    public JaxRS2ClientResponseAdapter(final ClientResponseContext response) {
        this.response = response;
    }

    @Override
    public int getStatusCode() {
        return response.getStatus();
    }
}
