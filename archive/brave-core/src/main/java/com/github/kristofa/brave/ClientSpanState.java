package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

/**
 * Maintains state for a single client span.
 *
 * <p/>Client spans can be at the following locations in the span tree.
 * <ul>
 *     <li>The root-span of a trace originated by Brave</li>
 *     <li>A child of a server span originated by Brave</li>
 *     <li>A child of a local span originated by Brave</li>
 * </ul>
 *
 * @author kristof
 */
public interface ClientSpanState extends CommonSpanState {

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
