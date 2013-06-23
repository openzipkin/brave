package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

/**
 * Maintains client span state.
 * 
 * @author kristof
 */
interface ClientSpanState extends CommonSpanState {

    /**
     * Gets the Span for the client request that was started as part of current request.
     * <p/>
     * Should be thread-aware to support multiple parallel requests.
     * 
     * @return Client request span for current thread.
     */
    Span getCurrentClientSpan();

    /**
     * Sets current client span.
     * <p/>
     * Should be thread-aware to support multiple parallel requests.
     * 
     * @param span Client span.
     */
    void setCurrentClientSpan(final Span span);
}
