package com.github.kristofa.brave.http;


public class DefaultSpanNameProvider implements SpanNameProvider {

    private final HttpClientRequest request;

    public DefaultSpanNameProvider(HttpClientRequest request) {
        this.request = request;
    }

    @Override
    public String spanName() {
        return request.getHttpMethod();
    }
}
