package com.github.kristofa.brave.client;

import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

class SpringResponseAdapter implements ClientResponseAdapter {

    private final ClientHttpResponse response;

    SpringResponseAdapter(final ClientHttpResponse response) {
        this.response = response;
    }

    @Override
    public int getStatusCode() {
        try {
            return response.getRawStatusCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    ClientHttpResponse getResponse() {
        return response;
    }
}
