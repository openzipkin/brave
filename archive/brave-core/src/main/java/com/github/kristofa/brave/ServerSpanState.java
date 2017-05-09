package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;

/**
 * Maintains state for a single server span.
 *
 * <p/>Server spans can be at the following locations in the span tree.
 * <ul>
 *     <li>The root-span of a trace originated by Brave</li>
 *     <li>A child of a span propagated to Brave</li>
 * </ul>
 *
 * @author kristof
 */
public interface ServerSpanState extends CommonSpanState {

    /**
     * Gets the Span for the server request we are currently part of.
     * <p/>
     * Should be thread-aware to support multiple parallel requests.
     * 
     * @return Server request span for current thread. This will return the span we are part of. In case we should not trace
     *         current request <code>null</code> will be returned.
     */
    @Nullable
    ServerSpan getCurrentServerSpan();

    /**
     * Set span for current request.
     * 
     * @param span Span for current request.
     */
    void setCurrentServerSpan(final ServerSpan span);
}
