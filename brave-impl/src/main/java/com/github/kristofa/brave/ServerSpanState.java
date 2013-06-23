package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

/**
 * Maintains server span state.
 * 
 * @author kristof
 */
interface ServerSpanState extends CommonSpanState {

    /**
     * Gets the Span for the server request we are currently part of.
     * <p/>
     * Should be thread-aware to support multiple parallel requests.
     * 
     * @return Server request span for current thread. This will return the span we are part of. In case we should not trace
     *         current request <code>null</code> will be returned.
     */
    Span getCurrentServerSpan();

    /**
     * Set span for current request.
     * 
     * @param span Span for current request.
     */
    void setCurrentServerSpan(final Span span);

}
