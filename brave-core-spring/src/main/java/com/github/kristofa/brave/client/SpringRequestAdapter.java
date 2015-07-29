package com.github.kristofa.brave.client;

import com.github.kristofa.brave.BraveHttpHeaders;
import org.springframework.http.HttpRequest;

import java.net.URI;

class SpringRequestAdapter implements ClientRequestAdapter {

    private final HttpRequest request;

    SpringRequestAdapter(final HttpRequest request) {
        this.request = request;
    }

    @Override
    public URI getUri() {
        return request.getURI();
    }

    @Override
    public String getMethod() {
        return request.getMethod().name();
    }

    @Override
    public String getSpanName() {
        return request.getHeaders().getFirst(BraveHttpHeaders.SpanName.getName());
    }

    @Override
    public void addHeader(final String header, final String value) {
        request.getHeaders().add(header, value);
    }

    HttpRequest getRequest() {
        return request;
    }
}

