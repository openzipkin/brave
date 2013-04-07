package com.github.kristofa.brave;

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
     * Get parent span id.
     * 
     * @return Parent span id. Can be <code>null</code>.
     */
    Long getParentSpanId();

}
