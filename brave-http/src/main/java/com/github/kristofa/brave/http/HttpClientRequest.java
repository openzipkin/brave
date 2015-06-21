package com.github.kristofa.brave.http;


import java.net.URI;

public interface HttpClientRequest {

    /**
     * Adds headers to request.
     *
     * @param header header name.
     * @param value header value.
     */
    void addHeader(String header, String value);

    /**
     * Get request URI.
     *
     * @return Request URI.
     */
    URI getUri();

    /**
     * Returns the http method for request (GET, PUT, POST,...)
     *
     * @return Http Method for request.
     */
    String getHttpMethod();


}
