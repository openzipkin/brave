package com.github.kristofa.brave.http;


public interface HttpServerRequest {

    String getHttpHeaderValue(String headerName);
}
