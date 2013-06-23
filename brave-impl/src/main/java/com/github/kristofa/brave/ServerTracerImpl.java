package com.github.kristofa.brave;

import org.apache.commons.lang3.Validate;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Endpoint;
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

    /**
     * Creates a new instance.
     * 
     * @param state Server span state.
     * @param spanCollector Span collector.
     */
    public ServerTracerImpl(final ServerSpanState state, final SpanCollector spanCollector) {
        Validate.notNull(state);
        Validate.notNull(spanCollector);
        this.state = state;
        collector = spanCollector;
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
        submit(annotationName, duration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName) {
        if (!state.shouldTrace()) {
            return;
        }
        submit(annotationName, null);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setServerReceived() {
        if (!state.shouldTrace()) {
            return;
        }
        submit(zipkinCoreConstants.SERVER_RECV, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setServerSend() {
        if (!state.shouldTrace()) {
            return;
        }
        final Span currentServerSpan = submit(zipkinCoreConstants.SERVER_SEND, null);
        if (currentServerSpan != null) {
            collector.collect(currentServerSpan);
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

    private Span submit(final String annotationName, final Integer duration) {
        final Span currentSpan = state.getCurrentServerSpan();
        if (currentSpan != null) {
            final Endpoint endPoint = state.getEndPoint();
            if (endPoint != null) {
                final Annotation annotation = new Annotation();
                if (duration != null) {
                    annotation.setDuration(duration * 1000);
                }
                annotation.setTimestamp(currentTimeMicroseconds());
                annotation.setHost(endPoint);
                annotation.setValue(annotationName);

                currentSpan.addToAnnotations(annotation);
                return currentSpan;
            }
        }
        return null;
    }

}
