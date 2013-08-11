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

        state.setCurrentServerSpan(new ServerSpanImpl(traceId, spanId, parentSpanId, name));
    }

    @Override
    public void setNoSampling() {
        state.setCurrentServerSpan(new ServerSpanImpl(false));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName, final long startTime, final long endTime) {

        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), annotationName, startTime, endTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName) {

        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), annotationName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitBinaryAnnotation(final String key, final String value) {

        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitBinaryAnnotation(currentSpan, state.getEndPoint(), key, value);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitBinaryAnnotation(final String key, final int value) {
        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitBinaryAnnotation(currentSpan, state.getEndPoint(), key, value);
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
        final Span currentSpan = state.getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), zipkinCoreConstants.SERVER_SEND);
            final long threadDuration = state.getServerSpanThreadDuration();
            if (threadDuration > 0) {
                annotationSubmitter.submitBinaryAnnotation(currentSpan, state.getEndPoint(),
                    BraveAnnotations.THREAD_DURATION, String.valueOf(threadDuration));
            }

            collector.collect(currentSpan);
            state.setCurrentServerSpan(null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getThreadDuration() {
        return state.getServerSpanThreadDuration();
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
