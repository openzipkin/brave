package com.github.kristofa.brave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang3.Validate;

import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

/**
 * {@link ServerTracer} implementation.
 * 
 * @author kristof
 */
class ServerTracerImpl extends AbstractAnnotationSubmitter implements ServerTracer {

    private final ServerSpanState state;
    private final SpanCollector collector;
    private final List<TraceFilter> traceFilters = new ArrayList<TraceFilter>();
    private final Random randomGenerator;

    /**
     * Creates a new instance.
     * 
     * @param state Server span state.
     * @param spanCollector Span collector.
     * @param traceFilters Trace Filters.
     */
    ServerTracerImpl(final ServerSpanState state, final Random randomGenerator, final SpanCollector spanCollector,
    		final List<TraceFilter> traceFilters) {
        Validate.notNull(state);
        Validate.notNull(spanCollector);
        Validate.notNull(traceFilters);
        Validate.notNull(randomGenerator);
        this.state = state;
        collector = spanCollector;
        this.traceFilters.addAll(traceFilters);
        this.randomGenerator = randomGenerator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Span getSpan() {
        return state.getCurrentServerSpan().getSpan();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Endpoint getEndPoint() {
        return state.getEndPoint();
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
    public void setStateCurrentTrace(final long traceId, final long spanId, final Long parentSpanId, final String name) {
        Validate.notEmpty(name, "Span name can't be empty or null.");
        state.setCurrentServerSpan(new ServerSpanImpl(traceId, spanId, parentSpanId, name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStateNoTracing() {
        state.setCurrentServerSpan(new ServerSpanImpl(false));

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStateUnknown(final String spanName) {
        Validate.notBlank(spanName, "Span name should not be null.");
        for (final TraceFilter traceFilter : traceFilters) {
            if (traceFilter.trace(spanName) == false) {
                state.setCurrentServerSpan(new ServerSpanImpl(false));
                return;
            }
        }
        final long newSpanId = randomGenerator.nextLong();
        state.setCurrentServerSpan(new ServerSpanImpl(newSpanId, newSpanId, null, spanName));
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
            submitAnnotation(zipkinCoreConstants.SERVER_SEND);
            final long threadDuration = state.getServerSpanThreadDuration();
            if (threadDuration > 0) {
                submitBinaryAnnotation(BraveAnnotations.THREAD_DURATION, String.valueOf(threadDuration));
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

    List<TraceFilter> getTraceFilters() {
        return Collections.unmodifiableList(traceFilters);
    }

}
