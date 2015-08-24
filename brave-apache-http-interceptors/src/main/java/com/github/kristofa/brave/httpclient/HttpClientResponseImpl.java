package com.github.kristofa.brave.httpclient;

import com.github.kristofa.brave.http.HttpResponse;


class HttpClientResponseImpl implements HttpResponse {

    private final org.apache.http.HttpResponse response;

    public HttpClientResponseImpl(org.apache.http.HttpResponse response) {
        this.response = response;
    }

    @Override
    public int getHttpStatusCode() {
        return response.getStatusLine().getStatusCode();
    }
}
