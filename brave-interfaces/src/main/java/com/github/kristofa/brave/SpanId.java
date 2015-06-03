package com.github.kristofa.brave;

import java.util.Optional;

/**
 * Identifies a {@link Span}.
 * 
 * @author kristof
 */
public interface SpanId {

    /**
     * Get Trace id.
     * 
     * @return Trace id.
     */
    long getTraceId();

    /**
     * Get span id.
     * 
     * @return span id.
     */
    long getSpanId();

    /**
     * Deprecated. Please use getOptionalParentSpanId().
     *
     * @return Parent span id. Can be <code>null</code>.
     */
    @Deprecated
    Long getParentSpanId();

    /**
     * Get parent span id.
     *
     * @return Optional parent span id.
     */
    Optional<Long> getOptionalParentSpanId();

}
