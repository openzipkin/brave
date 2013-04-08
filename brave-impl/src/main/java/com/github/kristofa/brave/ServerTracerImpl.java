package com.github.kristofa.brave;

import org.apache.commons.lang3.Validate;

/**
 * {@link ServerTracer} implementation that traces every request if there is not indication not to trace request.
 * 
 * @author kristof
 */
class ServerTracerImpl implements ServerTracer {

    private final static String SERVER_RECEIVED = "sr";
    private final static String SERVER_SEND = "ss";

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
    public void setSpan(final long traceId, final long spanId, final Long parentSpanId, final String name) {
        final SpanImpl spanImpl = new SpanImpl(new SpanIdImpl(traceId, spanId, parentSpanId), name);
        state.setCurrentServerSpan(spanImpl);
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
    public void submitAnnotation(final String annotationName, final long duration) {
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
        submit(SERVER_RECEIVED, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setServerSend() {
        if (!state.shouldTrace()) {
            return;
        }
        final Span currentServerSpan = submit(SERVER_SEND, null);
        if (currentServerSpan != null) {
            collector.collect(currentServerSpan);
            state.setCurrentServerSpan(null);
        }
    }

    private Span submit(final String annotationName, final Long duration) {
        final Span currentSpan = state.getCurrentServerSpan();
        if (currentSpan != null) {
            final EndPoint endPoint = state.getEndPoint();
            if (endPoint != null) {
                currentSpan.addAnnotation(new AnnotationImpl(annotationName, endPoint, duration));
                return currentSpan;
            }
        }
        return null;
    }

}
