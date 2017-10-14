package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Span;

/**
 * The ServerSpan is initialized by {@link ServerTracer} and keeps track of Trace/Span state of our service request.
 *
 * @author adriaens
 * @deprecated Replaced by {@code brave.propagation.TraceContext}
 */
@Deprecated
@AutoValue
public abstract class ServerSpan {

    public static final ServerSpan EMPTY = new AutoValue_ServerSpan(null, null, null);
    static final ServerSpan NOT_SAMPLED = new AutoValue_ServerSpan(null, null, false);

    @Nullable
    abstract SpanId spanId();

    /**
     * Gets the Trace/Span context.
     *
     * @return Trace/Span context. Can be <code>null</code> in case we did not get any context in request.
     */
    @Nullable
    public abstract Span getSpan();

    /**
     * Indicates if we need to sample this request or not.
     *
     * @return <code>true</code> in case we should sample this request, <code>false</code> in case we should not sample this
     *         request or <code>null</code> in case we did not get any indication about sampling this request. In this case
     *         new client requests should decide about sampling or not.
     */
    @Nullable
    public abstract Boolean getSample();

    /** Converts the input into a new server span or {@linkplain ServerSpan#NOT_SAMPLED}. */
    static ServerSpan create(Span span) {
        SpanId context = Brave.context(span);
        if (Boolean.FALSE.equals(context.sampled())) {
            return ServerSpan.NOT_SAMPLED;
        }
        return new AutoValue_ServerSpan(context, span, context.sampled());
    }

    ServerSpan(){
    }
}
