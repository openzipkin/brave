package com.github.kristofa.brave;

import java.util.*;

import org.apache.commons.lang3.Validate;

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
class ClientTracerImpl extends AbstractAnnotationSubmitter implements ClientTracer {

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
    Span getSpan() {
        return state.getCurrentClientSpan();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Endpoint getEndPoint() {
        return state.getClientEndPoint();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientSent() {

        submitAnnotation(zipkinCoreConstants.CLIENT_SEND);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientReceived() {

        final Span currentSpan = getSpan();
        if (currentSpan != null) {
            submitAnnotation(zipkinCoreConstants.CLIENT_RECV);
            spanCollector.collect(currentSpan);
            state.setCurrentClientSpan(null);
            state.setCurrentClientServiceName(null);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpanId startNewSpan(final String requestName) {

        final Boolean sample = state.sample();
        if (Boolean.FALSE.equals(sample)) {
            state.setCurrentClientSpan(null);
            state.setCurrentClientServiceName(null);
            return null;
        }

        if (sample == null) {
            // No sample indication is present.
            for (final TraceFilter traceFilter : traceFilters) {
                if (!traceFilter.trace(requestName)) {
                    state.setCurrentClientSpan(null);
                    state.setCurrentClientServiceName(null);
                    return null;
                }
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
    public void setCurrentClientServiceName(final String serviceName) {
        state.setCurrentClientServiceName(serviceName);
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

    private SpanId getNewSpanId() {

        final Span currentServerSpan = state.getCurrentServerSpan().getSpan();
        final long newSpanId = randomGenerator.nextLong();
        if (currentServerSpan == null) {
            return new SpanId(newSpanId, newSpanId, null);
        }

        return new SpanId(currentServerSpan.getTrace_id(), newSpanId, currentServerSpan.getId());
    }

}
