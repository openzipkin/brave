package com.github.kristofa.brave;

import java.util.Objects;
import java.util.Optional;

/**
 * Identifies a Span.
 * 
 * @author kristof
 */
public class SpanId {

    private final long traceId;
    private final long spanId;
    private final Optional<Long> parentSpanId;

    /**
     * Creates a new span id.
     *
     * @param traceId Trace Id.
     * @param spanId Span Id.
     * @param parentSpanId Optional parent span id.
     */
    public SpanId(final long traceId, final long spanId, final Optional<Long> parentSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = Objects.requireNonNull(parentSpanId, "null is not allowed, you should use Optional.empty()");
    }

    /**
     * Get Trace id.
     *
     * @return Trace id.
     */
    public long getTraceId() {
        return traceId;
    }

    /**
     * Get span id.
     *
     * @return span id.
     */
    public long getSpanId() {
        return spanId;
    }

    /**
     * Deprecated. Please use getOptionalParentSpanId().
     *
     * @return Parent span id. Can be <code>null</code>.
     */
    @Deprecated
    public Long getParentSpanId() {
        return parentSpanId.orElse(null);
    }

    /**
     * Get parent span id.
     *
     * @return Optional parent span id.
     */
    public Optional<Long> getOptionalParentSpanId() {
        return parentSpanId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, spanId, parentSpanId);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof SpanId) {
            final SpanId other = (SpanId) obj;
            return Objects.equals(this.traceId, other.traceId) &&
                    Objects.equals(this.spanId, other.spanId) &&
                    Objects.equals(this.parentSpanId, other.parentSpanId);
        }
        else
        {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[trace id: " + traceId + ", span id: " + spanId + ", parent span id: "
                + parentSpanId.toString() + "]";
    }
}
