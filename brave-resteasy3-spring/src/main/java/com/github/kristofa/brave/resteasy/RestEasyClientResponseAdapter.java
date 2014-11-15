package com.github.kristofa.brave.resteasy;

import org.jboss.resteasy.client.ClientResponse;

import com.github.kristofa.brave.ClientResponseAdapter;

class RestEasyClientResponseAdapter implements ClientResponseAdapter {

    private final ClientResponse<?> clientResponse;

    public RestEasyClientResponseAdapter(final ClientResponse<?> clientResponse) {
        this.clientResponse = clientResponse;
    }

    @Override
    public int getStatusCode() {
        if (clientResponse == null) {
            return 0;
        }
        return clientResponse.getStatus();
    }
}
