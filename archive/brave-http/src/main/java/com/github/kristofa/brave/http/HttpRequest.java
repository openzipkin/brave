package com.github.kristofa.brave.http;


import java.net.URI;

public interface HttpRequest {

    /**
     * Returns the entire URL, including the scheme, host and query parameters if available.
     *
     * @return Request URI.
     *
     * @see zipkin.TraceKeys#HTTP_URL
     */
    URI getUri();

    /**
     * Returns the http method for request (GET, PUT, POST,...)
     *
     * @return Http Method for request.
     */
    String getHttpMethod();

}
