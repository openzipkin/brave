package com.github.kristofa.brave;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.twitter.zipkin.gen.Span;

/**
 * {@link ServerSpan} implementation.
 * <p/>
 * Contains state of server span.
 * 
 * @author kristof
 */
class ServerSpanImpl implements ServerSpan {

    private final Span span;
    private final AtomicLong threadDuration = new AtomicLong();
    private final Boolean sample;

    /**
     * Creates a new initializes instance. Using this constructor also indicates we need to sample this request.
     * 
     * @param traceId Trace id.
     * @param spanId Span id.
     * @param parentSpanId Parent span id, can be <code>null</code>.
     * @param name Span name.
     */
    ServerSpanImpl(final long traceId, final long spanId, final Long parentSpanId, final String name) {

        span = new Span();
        span.setTrace_id(traceId);
        span.setId(spanId);
        if (parentSpanId != null) {
            span.setParent_id(parentSpanId);
        }
        span.setName(name);
        sample = true;
    }

    /**
     * Creates a new empty instance with no Span but with sample indication.
     *
     * @param sample Indicates if we should sample this span.
     */
    ServerSpanImpl(final Boolean sample) {
        span = null;
        this.sample = sample;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span getSpan() {
        return span;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incThreadDuration(final long durationMs) {
        threadDuration.addAndGet(durationMs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getThreadDuration() {
        return threadDuration.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean getSample() {
        return sample;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, "threadDuration");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj, "threadDuration");
    }

}
