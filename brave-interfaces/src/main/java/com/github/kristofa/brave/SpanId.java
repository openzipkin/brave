package com.github.kristofa.brave;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.annotation.Nullable;

/**
 * Identifies a Span.
 * 
 * @author kristof
 */
public class SpanId {

    private final long traceId;
    private final long spanId;
    private final Long parentSpanId;

    /**
     * Creates a new span id.
     *
     * @param traceId Trace Id.
     * @param spanId Span Id.
     * @param parentSpanId Nullable parent span id.
     */
    public SpanId(final long traceId, final long spanId, @Nullable final Long parentSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
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
     * Get parent span id.
     *
     * @return Parent span id. Can be <code>null</code>.
     */
    @Nullable
    public Long getParentSpanId() {
        return parentSpanId;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(traceId).append(spanId).append(parentSpanId).toHashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof SpanId) {
            final SpanId other = (SpanId) obj;
            return new EqualsBuilder()
                .append(this.traceId, other.traceId)
                .append(this.spanId, other.spanId)
                .append(this.parentSpanId, other.parentSpanId).isEquals();
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
                + parentSpanId + "]";
    }
}
