package com.github.kristofa.brave;

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
    private final TraceFilter traceFilter;

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

        if (traceFilter.shouldTrace(requestName) == false) {
            state.setTracing(false);
            return null;
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

    TraceFilter getTraceFilter() {
        return traceFilter;
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
            final long newTraceId = randomGenerator.nextLong();
            return new SpanIdImpl(newTraceId, newSpanId, null);
        }

        return new SpanIdImpl(currentServerSpan.getTrace_id(), newSpanId, currentServerSpan.getId());
    }

}
