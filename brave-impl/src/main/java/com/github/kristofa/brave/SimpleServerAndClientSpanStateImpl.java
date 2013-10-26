package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;

/**
 * {@link ServerAndClientSpanState} implementation that is used with {@link BraveContext}.
 * 
 * @see BraveContext
 */
class SimpleServerAndClientSpanStateImpl implements ServerAndClientSpanState {

    private Endpoint endPoint;
    private Span currentClientSpan;
    private ServerSpan currentServerSpan;

    /**
     * {@inheritDoc}
     */
    @Override
    public ServerSpan getCurrentServerSpan() {
        return currentServerSpan;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentServerSpan(final ServerSpan span) {
        currentServerSpan = span;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Endpoint getEndPoint() {
        return endPoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEndPoint(final Endpoint endPoint) {
        this.endPoint = endPoint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Span getCurrentClientSpan() {
        return currentClientSpan;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCurrentClientSpan(final Span span) {
        currentClientSpan = span;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void incrementServerSpanThreadDuration(final long durationMs) {
        currentServerSpan.incThreadDuration(durationMs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getServerSpanThreadDuration() {
        return currentServerSpan.getThreadDuration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean sample() {
        return currentServerSpan.getSample();
    }

}
