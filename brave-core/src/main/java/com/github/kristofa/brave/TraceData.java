package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;

/**
 * Trace properties we potentially get from incoming request.
 */
@AutoValue
public abstract class TraceData {

    public static Builder builder(){
        return new AutoValue_TraceData.Builder();
    }

    /**
     * Span id.
     *
     * @return Nullable Span id.
     */
    @Nullable
    public abstract SpanId getSpanId();

    /**
     * Indication of request should be sampled or not.
     *
     * @return Nullable Indication if request should be sampled or not.
     */
    @Nullable
    public abstract Boolean getSample();

    @AutoValue.Builder
    public interface Builder {

        Builder spanId(@Nullable SpanId spanId);

        Builder sample(@Nullable Boolean sample);

        TraceData build();
    }
}
