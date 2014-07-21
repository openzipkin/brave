package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.ClientResponseAdapter;
import org.jboss.resteasy.client.ClientResponse;

public class RestEasyClientResponseAdapter implements ClientResponseAdapter {
    private final ClientResponse clientResponse;

    public RestEasyClientResponseAdapter(ClientResponse clientResponse) {
        this.clientResponse = clientResponse;
    }

    @Override
    public int getStatusCode() {
        if(clientResponse == null) {
            return 0;
        }
        return clientResponse.getStatus();
    }
}
