package com.github.kristofa.brave;

import java.util.Objects;
import java.util.Optional;

/**
 * Trace properties we potentially get from incoming request.
 */
public class TraceData {

    private final Optional<SpanId> spanId;
    private final Optional<Boolean> sample;

    public static class Builder {

        private Optional<SpanId> spanId = Optional.empty();
        private Optional<Boolean> sample = Optional.empty();

        public Builder spanId(SpanId spanId) {
            this.spanId = Optional.of(spanId);
            return this;
        }

        public Builder sample(boolean sample) {
            this.sample = Optional.of(sample);
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
     * @return Span id.
     */
    public Optional<SpanId> getSpanId() {
        return spanId;
    }

    /**
     * Indication of request should be sampled or not.
     *
     * @return Indication if request should be sampled or not.
     */
    public Optional<Boolean> getSample() {
        return sample;
    }

    @Override
    public int hashCode() {
        return Objects.hash(spanId, sample);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof TraceData) {
            final TraceData other = (TraceData) obj;
            return Objects.equals(this.sample, other.sample) &&
                    Objects.equals(this.spanId, other.spanId);
        }
        return false;
    }
}
