package com.github.kristofa.brave.httpclient;


import com.github.kristofa.brave.http.HttpClientRequest;
import org.apache.http.HttpRequest;

import java.net.URI;

class HttpClientRequestImpl implements HttpClientRequest {

    private final HttpRequest request;

    public HttpClientRequestImpl(HttpRequest request) {
        this.request = request;
    }

    @Override
    public void addHeader(String header, String value) {
        request.setHeader(header, value);
    }

    @Override
    public URI getUri() {
        return URI.create(request.getRequestLine().getUri());
    }

    @Override
    public String getHttpMethod() {
        return request.getRequestLine().getMethod();
    }
}
