package com.github.kristofa.brave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.Validate;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Endpoint;
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

    /**
     * Creates a new instance.
     * 
     * @param state Current span state.
     * @param randomGenerator Used to generate new trace/span ids.
     * @param spanCollector Will collect the spans.
     * @param traceFilters List of TraceFilters. Will be executed in order. If one returns <code>false</code> there will be
     *            no tracing and next ones will not be executed anymore. So order is important.
     */
    ClientTracerImpl(final ServerAndClientSpanState state, final Random randomGenerator, final SpanCollector spanCollector,
        final List<TraceFilter> traceFilters) {
        Validate.notNull(state);
        Validate.notNull(randomGenerator);
        Validate.notNull(spanCollector);
        Validate.notNull(traceFilters);
        this.state = state;
        this.randomGenerator = randomGenerator;
        this.spanCollector = spanCollector;
        this.traceFilters.addAll(traceFilters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientSent() {

        if (state.shouldTrace() == false) {
            return;
        }
        submit(zipkinCoreConstants.CLIENT_SEND, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientReceived() {

        if (state.shouldTrace() == false) {
            return;
        }
        final Span currentSpan = submit(zipkinCoreConstants.CLIENT_RECV, null);
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

    private Span submit(final String annotationName, final Integer duration) {
        final Span currentSpan = state.getCurrentClientSpan();
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

    private SpanId getNewSpanId() {

        final Span currentServerSpan = state.getCurrentServerSpan();
        final long newSpanId = randomGenerator.nextLong();
        if (currentServerSpan == null) {
            return new SpanIdImpl(newSpanId, newSpanId, null);
        }

        return new SpanIdImpl(currentServerSpan.getTrace_id(), newSpanId, currentServerSpan.getId());
    }

}
