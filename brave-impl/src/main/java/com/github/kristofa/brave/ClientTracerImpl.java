package com.github.kristofa.brave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.Validate;

import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

/**
 * {@link ClientTracer} implementation that is configurable using a {@link TraceFilter} with which for example sampling can
 * be implemented.
 * 
 * @see ClientTracer
 * @author kristof
 */
class ClientTracerImpl implements ClientTracer {

    private final ServerAndClientSpanState state;
    private final Random randomGenerator;
    private final SpanCollector spanCollector;
    private final List<TraceFilter> traceFilters = new ArrayList<TraceFilter>();
    private final CommonAnnotationSubmitter annotationSubmitter;

    /**
     * Creates a new instance.
     * 
     * @param state Current span state.
     * @param randomGenerator Used to generate new trace/span ids.
     * @param spanCollector Will collect the spans.
     * @param traceFilters List of TraceFilters. Will be executed in order. If one returns <code>false</code> there will be
     *            no tracing and next ones will not be executed anymore. So order is important.
     * @param commonAnnotationSubmitter Common Annotation Submitter.
     */
    ClientTracerImpl(final ServerAndClientSpanState state, final Random randomGenerator, final SpanCollector spanCollector,
        final List<TraceFilter> traceFilters, final CommonAnnotationSubmitter commonAnnotationSubmitter) {
        Validate.notNull(state);
        Validate.notNull(randomGenerator);
        Validate.notNull(spanCollector);
        Validate.notNull(traceFilters);
        Validate.notNull(commonAnnotationSubmitter);
        this.state = state;
        this.randomGenerator = randomGenerator;
        this.spanCollector = spanCollector;
        this.traceFilters.addAll(traceFilters);
        annotationSubmitter = commonAnnotationSubmitter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientSent() {

        final Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), zipkinCoreConstants.CLIENT_SEND);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientReceived() {

        final Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), zipkinCoreConstants.CLIENT_RECV);
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

        for (final TraceFilter traceFilter : traceFilters) {
            if (traceFilter.shouldTrace(requestName) == false) {
                state.setTracing(false);
                return null;
            }
        }

        final SpanId newSpanId = getNewSpanId();
        final Span newSpan = new Span();
        newSpan.setId(newSpanId.getSpanId());
        newSpan.setTrace_id(newSpanId.getTraceId());
        if (newSpanId.getParentSpanId() != null) {
            newSpan.setParent_id(newSpanId.getParentSpanId());
        }
        newSpan.setName(requestName);
        state.setCurrentClientSpan(newSpan);
        return newSpanId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName, final long startTime, final long endTime) {
        final Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), annotationName, startTime, endTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitAnnotation(final String annotationName) {

        final Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitAnnotation(currentSpan, state.getEndPoint(), annotationName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitBinaryAnnotation(final String key, final String value) {

        final Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitBinaryAnnotation(currentSpan, state.getEndPoint(), key, value);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void submitBinaryAnnotation(final String key, final int value) {

        final Span currentSpan = getCurrentSpan();
        if (currentSpan != null) {
            annotationSubmitter.submitBinaryAnnotation(currentSpan, state.getEndPoint(), key, value);
        }

    }

    ServerAndClientSpanState getServerAndClientSpanState() {
        return state;
    }

    SpanCollector getSpanCollector() {
        return spanCollector;
    }

    List<TraceFilter> getTraceFilters() {
        return Collections.unmodifiableList(traceFilters);
    }

    long currentTimeMicroseconds() {
        return System.currentTimeMillis() * 1000;
    }

    private SpanId getNewSpanId() {

        final Span currentServerSpan = state.getCurrentServerSpan();
        final long newSpanId = randomGenerator.nextLong();
        if (currentServerSpan == null) {
            return new SpanIdImpl(newSpanId, newSpanId, null);
        }

        return new SpanIdImpl(currentServerSpan.getTrace_id(), newSpanId, currentServerSpan.getId());
    }

    private Span getCurrentSpan() {
        if (state.shouldTrace() == false) {
            return null;
        }
        return state.getCurrentClientSpan();
    }

}
