package com.github.kristofa.brave;

/**
 * Identifies a Span.
 * 
 * @author kristof
 */
class SpanIdImpl implements SpanId {

    private final long traceId;
    private final long spanId;
    private final Long parentSpanId;

    /**
     * Creates a new span id.
     * 
     * @param traceId Trace Id.
     * @param spanId Span Id.
     * @param parentSpanId Optional parent span id.
     */
    SpanIdImpl(final long traceId, final long spanId, final Long parentSpanId) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTraceId() {
        return traceId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSpanId() {
        return spanId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getParentSpanId() {
        return parentSpanId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (parentSpanId == null ? 0 : parentSpanId.hashCode());
        result = prime * result + (int)(spanId ^ spanId >>> 32);
        result = prime * result + (int)(traceId ^ traceId >>> 32);
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final SpanIdImpl other = (SpanIdImpl)obj;
        if (parentSpanId == null) {
            if (other.parentSpanId != null) {
                return false;
            }
        } else if (!parentSpanId.equals(other.parentSpanId)) {
            return false;
        }
        if (spanId != other.spanId) {
            return false;
        }
        if (traceId != other.traceId) {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "[trace id: " + traceId + ", span id: " + spanId + ", parent span id: "
            + (parentSpanId == null ? "null" : parentSpanId.toString()) + "]";
    }

}
