package com.github.kristofa.brave;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.annotation.Nullable;

/**
 * Trace properties we potentially get from incoming request.
 */
public class TraceData {

    private final SpanId spanId;
    private final Boolean sample;

    public static class Builder {

        private SpanId spanId;
        private Boolean sample;

        public Builder spanId(@Nullable SpanId spanId) {
            this.spanId = spanId;
            return this;
        }

        public Builder sample(@Nullable boolean sample) {
            this.sample = sample;
            return this;
        }

        public TraceData build() {
            return new TraceData(this);
        }

    }

    private TraceData(Builder builder) {
        this.spanId = builder.spanId;
        this.sample = builder.sample;
    }

    /**
     * Span id.
     *
     * @return Nullable Span id.
     */
    @Nullable
    public SpanId getSpanId() {
        return spanId;
    }

    /**
     * Indication of request should be sampled or not.
     *
     * @return Nullable Indication if request should be sampled or not.
     */
    @Nullable
    public Boolean getSample() {
        return sample;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(spanId).append(sample).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof TraceData) {
            final TraceData other = (TraceData) obj;
            return new EqualsBuilder()
                .append(this.sample, other.sample)
                .append(this.spanId, other.spanId).isEquals();
        }
        return false;
    }
}
