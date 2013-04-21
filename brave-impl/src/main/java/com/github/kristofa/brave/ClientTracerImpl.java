package com.github.kristofa.brave;

import java.util.Random;

import org.apache.commons.lang3.Validate;

/**
 * {@link ClientTracer} implementation that is configurable using a {@link TraceFilter} with which for example sampling can
 * be implemented.
 * 
 * @see ClientTracer
 * @author kristof
 */
class ClientTracerImpl implements ClientTracer {

    private final static String CLIENT_SEND = "cs";
    private final static String CLIENT_RECEIVED = "cr";

    private final ServerAndClientSpanState state;
    private final Random randomGenerator;
    private final SpanCollector spanCollector;
    private final TraceFilter traceFilter;

    /**
     * Creates a new instance which will trace all requests except if parent span already indicates we should not trace
     * current request.
     * 
     * @param state Current span state.
     * @param randomGenerator Used to generate new trace/span ids.
     * @param spanCollector Will collect the spans.
     */
    ClientTracerImpl(final ServerAndClientSpanState state, final Random randomGenerator, final SpanCollector spanCollector) {
        this(state, randomGenerator, spanCollector, new TraceAllTraceFilter());
    }

    /**
     * Creates a new instance.
     * 
     * @param state Current span state.
     * @param randomGenerator Used to generate new trace/span ids.
     * @param spanCollector Will collect the spans.
     * @param traceFilter Allows filtering of traces.
     */
    ClientTracerImpl(final ServerAndClientSpanState state, final Random randomGenerator, final SpanCollector spanCollector,
        final TraceFilter traceFilter) {
        Validate.notNull(state);
        Validate.notNull(randomGenerator);
        Validate.notNull(spanCollector);
        Validate.notNull(traceFilter);
        this.state = state;
        this.randomGenerator = randomGenerator;
        this.spanCollector = spanCollector;
        this.traceFilter = traceFilter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientSent() {

        if (state.shouldTrace() == false) {
            return;
        }
        submit(CLIENT_SEND, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientReceived() {

        if (state.shouldTrace() == false) {
            return;
        }
        final Span currentSpan = submit(CLIENT_RECEIVED, null);
        if (currentSpan != null) {
            spanCollector.collect(currentSpan);
            state.setCurrentClientSpan(null);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpanId startNewSpan(final String requestName) {

        if (state.shouldTrace() == false) {
            return null;
        }

        if (traceFilter.shouldTrace(requestName) == false) {
            state.setTracing(false);
            return null;
        }

        final SpanId newSpanId = getNewSpanId();
        final Span newSpan = new SpanImpl(newSpanId, requestName);
        state.setCurrentClientSpan(newSpan);
        return newSpanId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName, final int duration) {
        if (state.shouldTrace() == false) {
            return;
        }
        submit(annotationName, duration);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName) {
        if (state.shouldTrace() == false) {
            return;
        }
        submit(annotationName, null);
    }

    private Span submit(final String annotationName, final Integer duration) {
        final Span currentSpan = state.getCurrentClientSpan();
        if (currentSpan != null) {
            final EndPoint endPoint = state.getEndPoint();
            if (endPoint != null) {
                currentSpan.addAnnotation(new AnnotationImpl(annotationName, endPoint, duration));
                return currentSpan;
            }
        }
        return null;
    }

    private SpanId getNewSpanId() {

        final Span currentServerSpan = state.getCurrentServerSpan();
        final long newSpanId = randomGenerator.nextLong();
        if (currentServerSpan == null) {
            final long newTraceId = randomGenerator.nextLong();
            return new SpanIdImpl(newTraceId, newSpanId, null);
        }
        return new SpanIdImpl(currentServerSpan.getSpanId().getTraceId(), newSpanId, currentServerSpan.getSpanId()
            .getSpanId());
    }

}
