package com.github.kristofa.brave.http;


import java.net.URI;

public interface HttpRequest {

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
