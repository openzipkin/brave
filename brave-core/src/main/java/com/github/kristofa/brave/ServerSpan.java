package com.github.kristofa.brave;

import java.util.concurrent.atomic.AtomicLong;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;

import com.twitter.zipkin.gen.Span;

/**
 * The ServerSpan is initialized by {@link ServerTracer} and keeps track of Trace/Span state of our service request.
 *
 * @author adriaens
 */
@AutoValue
public abstract class ServerSpan {

    static final ServerSpan NOT_SAMPLED = ServerSpan.create(false);

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

    static ServerSpan create(Span span, Boolean sample) {
        return new AutoValue_ServerSpan(span, sample);
    }

    /**
     * Creates a new initializes instance. Using this constructor also indicates we need to sample this request.
     *
     * @param traceId Trace id.
     * @param spanId Span id.
     * @param parentSpanId Parent span id, can be <code>null</code>.
     * @param name Span name.
     */
     static ServerSpan create(long traceId, long spanId, Long parentSpanId, String name) {
        Span span = new Span();
        span.setTrace_id(traceId);
        span.setId(spanId);
        if (parentSpanId != null) {
            span.setParent_id(parentSpanId);
        }
        span.setName(name);
        return create(span, true);
    }

    /**
     * Creates a new empty instance with no Span but with sample indication.
     *
     * @param sample Indicates if we should sample this span.
     */
    static ServerSpan create(final Boolean sample) {
        return create(null, sample);
    }

    ServerSpan(){
    }
}
