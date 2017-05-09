package com.github.kristofa.brave.http;


public interface SpanNameProvider {

    String spanName(HttpRequest request);
}
