package com.github.kristofa.brave;

import org.apache.commons.lang3.Validate;

import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

/**
 * {@link ServerTracer} implementation that traces every request if there is not indication not to trace request.
 * 
 * @author kristof
 */
class ServerTracerImpl implements ServerTracer {

    private final ServerSpanState state;
    private final SpanCollector collector;
    private final CommonAnnotationSubmitter annotationSubmitter;

    /**
     * Creates a new instance.
     * 
     * @param state Server span state.
     * @param spanCollector Span collector.
     * @param annotationSubmitter Annotation submitter.
     */
    public ServerTracerImpl(final ServerSpanState state, final SpanCollector spanCollector,
        final CommonAnnotationSubmitter annotationSubmitter) {
        Validate.notNull(state);
        Validate.notNull(spanCollector);
        Validate.notNull(annotationSubmitter);
        this.state = state;
        collector = spanCollector;
        this.annotationSubmitter = annotationSubmitter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearCurrentSpan() {
        state.setCurrentServerSpan(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSpan(final long traceId, final long spanId, final Long parentSpanId, final String name) {

        final Span span = new Span();
        span.setTrace_id(traceId);
        span.setId(spanId);
        if (parentSpanId != null) {
            span.setParent_id(parentSpanId);
        }
        span.setName(name);
        state.setCurrentServerSpan(span);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSpan(final Span span) {
        state.setCurrentServerSpan(span);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setShouldTrace(final boolean shouldTrace) {
        state.setTracing(shouldTrace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName, final int duration) {
        if (!state.shouldTrace()) {
            return;
        }
        final Span currentSpan = state.getCurrentServerSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), annotationName, duration);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName) {
        if (!state.shouldTrace()) {
            return;
        }
        final Span currentSpan = state.getCurrentServerSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), annotationName);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setServerReceived() {
        submitAnnotation(zipkinCoreConstants.SERVER_RECV);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setServerSend() {
        if (!state.shouldTrace()) {
            return;
        }
        final Span currentSpan = state.getCurrentServerSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), zipkinCoreConstants.SERVER_SEND);
            collector.collect(currentSpan);
            state.setCurrentServerSpan(null);
        }
    }

    ServerSpanState getServerSpanState() {
        return state;
    }

    SpanCollector getSpanCollector() {
        return collector;
    }

    long currentTimeMicroseconds() {
        return System.currentTimeMillis() * 1000;
    }

}
