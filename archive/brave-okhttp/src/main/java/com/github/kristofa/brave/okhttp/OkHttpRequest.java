package com.github.kristofa.brave.okhttp;


import com.github.kristofa.brave.http.HttpClientRequest;
import okhttp3.Request;

import java.net.URI;

class OkHttpRequest implements HttpClientRequest {

    private final Request.Builder requestBuilder;
    private final Request request;

    OkHttpRequest(Request.Builder requestBuilder, Request request) {
        this.requestBuilder = requestBuilder;
        this.request = request;
    }

    @Override
    public void addHeader(String header, String value) {
        requestBuilder.addHeader(header, value);
    }

    @Override
    public String getHttpMethod() {
        return request.method();
    }

    @Override
    public URI getUri() {
        return request.url().uri();
    }

}
