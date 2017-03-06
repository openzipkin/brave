package com.github.kristofa.brave.jaxrs2;

import com.github.kristofa.brave.http.HttpResponse;

import javax.ws.rs.client.ClientResponseContext;

public class JaxRs2HttpResponse implements HttpResponse {

    private final ClientResponseContext response;

    public JaxRs2HttpResponse(final ClientResponseContext response) {
        this.response = response;
    }

    @Override
    public int getHttpStatusCode() {
        return response.getStatus();
    }
}
