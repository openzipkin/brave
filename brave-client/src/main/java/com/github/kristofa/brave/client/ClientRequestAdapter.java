package com.github.kristofa.brave.client;

import java.net.URI;

/**
 * Provides the required properties that are used by ClientRequestInterceptor.
 *
 * @see ClientRequestInterceptor
 */
public interface ClientRequestAdapter {

    /**
     * Get request URI.
     *
     * @return Request URI.
     */
    URI getUri();

    /**
     * Gets Http method.
     *
     * @return Http method, GET, PUT, POST,...
     */
    String getMethod();

    /**
     * Get optional span name.
     *
     * @return nullable span name. In case span name is not provided ClientRequestInterceptor will
     * use a default way to build span name.
     */
    String getSpanName();

    /**
     * ClientRequestInterceptor will submit http headers using this method
     * so the trace state can be passed onto the next service.
     *
     * You should make sure the submitted headers are added to the request.
     *
     * @param header header name.
     * @param value header value.
     */
    void addHeader(String header, String value);
}
