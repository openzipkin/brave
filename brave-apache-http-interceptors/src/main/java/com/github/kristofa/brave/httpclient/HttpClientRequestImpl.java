package com.github.kristofa.brave.httpclient;


import com.github.kristofa.brave.http.HttpClientRequest;
import org.apache.http.HttpRequest;

import java.net.URI;
import java.net.URISyntaxException;

class HttpClientRequestImpl implements HttpClientRequest {

    private final HttpRequest request;

    public HttpClientRequestImpl(HttpRequest request) {
        this.request = request;
    }

    @Override
    public void addHeader(String header, String value) {
        request.addHeader(header, value);
    }

    @Override
    public URI getUri() {
        try {
            return new URI(request.getRequestLine().getUri());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getHttpMethod() {
        return request.getRequestLine().getMethod();
    }
}
