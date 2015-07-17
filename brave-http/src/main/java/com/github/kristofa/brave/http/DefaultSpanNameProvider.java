package com.github.kristofa.brave.http;


public class DefaultSpanNameProvider implements SpanNameProvider {

    @Override
    public String spanName(HttpRequest request) {
        return request.getHttpMethod();
    }
}
