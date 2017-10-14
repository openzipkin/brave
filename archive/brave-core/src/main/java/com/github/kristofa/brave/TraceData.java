package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;

/**
 * Trace properties we potentially get from incoming request.
 *
 * @deprecated Replaced by {@code brave.propagation.TraceContext}
 */
@Deprecated
@AutoValue
public abstract class TraceData {
    /** Indicates an uninstrumented caller. */
    public static final TraceData EMPTY = new AutoValue_TraceData(null, null);
    /** An caller didn't report this trace, and neither should this hop. */
    public static final TraceData NOT_SAMPLED = new AutoValue_TraceData(null, false);

    /**
     * @deprecated use {@link #create(SpanId)} or one of the constants.
     */
    @Deprecated
    public static Builder builder() {
        return new BuilderImpl();
    }

    public static TraceData create(SpanId spanId) {
        return new AutoValue_TraceData(spanId, spanId.sampled());
    }

    static final class BuilderImpl implements Builder {
        SpanId spanId;
        Boolean sample;

        @Override public Builder spanId(@Nullable SpanId spanId) {
            this.spanId = spanId;
            return this;
        }

        @Override public Builder sample(@Nullable Boolean sample) {
            this.sample = sample;
            return this;
        }

        @Override public TraceData build() {
            if (spanId == null) {
                return new AutoValue_TraceData(spanId, sample);
            }
            if (sample != null ) {
                return new AutoValue_TraceData(spanId.toBuilder().sampled(sample).build(), sample);
            }
            return new AutoValue_TraceData(spanId, spanId.sampled());
        }
    }

    /**
     * Returns span attributes propagated from the caller or null, if none were sent.
     */
    @Nullable
    public abstract SpanId getSpanId();

    /**
     * Returns the upstream sampling decision or null to make one here.
     *
     * @see SpanId#sampled()
     */
    @Nullable
    public abstract Boolean getSample();

    /**
     * @deprecated use {@link #create(SpanId)} or one of the constants.
     */
    @Deprecated
    public interface Builder {

        Builder spanId(@Nullable SpanId spanId);

        Builder sample(@Nullable Boolean sample);

        TraceData build();
    }
}
