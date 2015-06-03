package com.github.kristofa.brave.client;

/**
 * Provides the required properties as needed by ClientResponseInterceptor.
 *
 * @see ClientResponseInterceptor
 */
public interface ClientResponseAdapter {

    /**
     * Http status response code.
     *
     * @return Http status response code.
     */
    int getStatusCode();
}
