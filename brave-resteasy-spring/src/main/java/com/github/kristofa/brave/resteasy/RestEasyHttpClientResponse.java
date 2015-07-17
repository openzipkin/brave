package com.github.kristofa.brave.resteasy;

import com.github.kristofa.brave.http.HttpResponse;
import org.jboss.resteasy.client.ClientResponse;

class RestEasyHttpClientResponse implements HttpResponse {

    private final ClientResponse<?> response;

    public RestEasyHttpClientResponse(ClientResponse<?> response) {
        this.response = response;
    }

    @Override
    public int getHttpStatusCode() {
        return response.getStatus();
    }
}
