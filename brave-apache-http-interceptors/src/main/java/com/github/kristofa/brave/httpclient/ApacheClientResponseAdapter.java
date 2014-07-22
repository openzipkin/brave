package com.github.kristofa.brave.httpclient;

import org.apache.http.HttpResponse;

import com.github.kristofa.brave.ClientResponseAdapter;

class ApacheClientResponseAdapter implements ClientResponseAdapter {

    private final HttpResponse response;

    public ApacheClientResponseAdapter(final HttpResponse response) {
        this.response = response;
    }

    @Override
    public int getStatusCode() {
        return response.getStatusLine().getStatusCode();
    }
}
