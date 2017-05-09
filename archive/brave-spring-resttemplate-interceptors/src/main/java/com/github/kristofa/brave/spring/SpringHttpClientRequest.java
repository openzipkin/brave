package com.github.kristofa.brave.spring;

import com.github.kristofa.brave.http.HttpClientRequest;
import org.springframework.http.HttpRequest;

import java.net.URI;

class SpringHttpClientRequest implements HttpClientRequest {
    private final HttpRequest request;

    SpringHttpClientRequest(final HttpRequest request) {
        this.request = request;
    }

    @Override
    public void addHeader(final String header, final String value) {
        request.getHeaders().add(header, value);
    }

    @Override
    public URI getUri() {
        return request.getURI();
    }

    @Override
    public String getHttpMethod() {
        return request.getMethod().name();
    }
}
