package com.github.kristofa.brave;

import java.util.Objects;
import java.util.Optional;

/**
 * Trace properties we potentially get from incoming request.
 */
public class TraceData {

    private final Optional<Long> traceId;
    private final Optional<Long> spanId;
    private final Optional<Long> parentSpanId;
    private final Optional<Boolean> sample;

    public static class Builder {

        private  Optional<Long> traceId = Optional.empty();
        private  Optional<Long> spanId = Optional.empty();
        private  Optional<Long> parentSpanId = Optional.empty();
        private  Optional<Boolean> sample = Optional.empty();

        public Builder traceId(long traceId) {
            this.traceId = Optional.of(traceId);
            return this;
        }

        public Builder spanId(long spanId) {
            this.spanId = Optional.of(spanId);
            return this;
        }

        public Builder parentSpanId(long parentSpanId) {
            this.parentSpanId = Optional.of(parentSpanId);
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
        this.traceId = builder.traceId;
        this.spanId = builder.spanId;
        this.parentSpanId = builder.parentSpanId;
        this.sample = builder.sample;
    }

    /**
     * Trace id.
     *
     * @return Trace id.
     */
    public Optional<Long> getTraceId() {
        return traceId;
    }

    /**
     * Span id.
     *
     * @return Span id.
     */
    public Optional<Long> getSpanId() {
        return spanId;
    }

    /**
     * Parent span id.
     *
     * @return Optional parent span id.
     */
    public Optional<Long> getParentSpanId() {
        return parentSpanId;
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
        return Objects.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return Objects.equals(this, obj);
    }
}
