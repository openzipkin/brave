package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;

/**
 * Identifies a Span.
 * 
 * @author kristof
 */
@AutoValue
public abstract class SpanId {

    /**
     * Creates a new span id.
     *
     * @param traceId      Trace Id.
     * @param spanId       Span Id.
     * @param parentSpanId Nullable parent span id.
     */
    public static SpanId create(long traceId, long spanId, @Nullable Long parentSpanId) {
        return new AutoValue_SpanId(traceId, spanId, parentSpanId);
    }

    /**
     * Get Trace id.
     *
     * @return Trace id.
     */
    public abstract long getTraceId();

    /**
     * Get span id.
     *
     * @return span id.
     */
    public abstract long getSpanId();

    /**
     * Get parent span id.
     *
     * @return Parent span id. Can be <code>null</code>.
     */
    @Nullable
    public abstract Long getParentSpanId();

    @Override
    public String toString() {
        return "[trace id: " + getTraceId() + ", span id: " + getSpanId() + ", parent span id: "
               + getParentSpanId() + "]";
    }

    SpanId() {
    }
}
