package com.github.kristofa.brave;

import com.google.auto.value.AutoValue;

import com.github.kristofa.brave.SpanAndEndpoint.ClientSpanAndEndpoint;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

import java.util.List;
import java.util.Random;

/**
 * Low level api that deals with client side of a request:
 *
 * <ol>
 *     <li>Decide on tracing or not (sampling)</li>
 *     <li>Sending client set / client received annotations</li>
 * </ol>
 *
 * It is advised that you use ClientRequestInterceptor and ClientResponseInterceptor which build
 * upon ClientTracer and provide a higher level api.
 *
 * @see ClientRequestInterceptor
 * @see ClientResponseInterceptor
 * @author kristof
 */
@AutoValue
public abstract class ClientTracer extends AnnotationSubmitter {

    public static Builder builder() {
        return new AutoValue_ClientTracer.Builder();
    }

    @Override
    abstract ClientSpanAndEndpoint spanAndEndpoint();
    abstract Random randomGenerator();
    abstract SpanCollector spanCollector();
    abstract List<TraceFilter> traceFilters();

    @AutoValue.Builder
    public abstract static class Builder {

        public Builder state(ServerAndClientSpanState state) {
            return spanAndEndpoint(ClientSpanAndEndpoint.create(state));
        }

        abstract Builder spanAndEndpoint(ClientSpanAndEndpoint spanAndEndpoint);

        /**
         * Used to generate new trace/span ids.
         */
        public abstract Builder randomGenerator(Random randomGenerator);

        public abstract Builder spanCollector(SpanCollector spanCollector);

        /**
         * Will be executed in order. If one returns <code>false</code> there will be no tracing and
         * next ones will not be executed anymore. So order is important.
         */
        public abstract Builder traceFilters(List<TraceFilter> traceFilters);

        abstract ClientTracer build();
    }

    /**
     * Sets 'client sent' event for current thread.
     */
    public void setClientSent() {
        submitAnnotation(zipkinCoreConstants.CLIENT_SEND);
    }

    /**
     * Sets the 'client received' event for current thread. This will also submit span because setting a client received
     * event means this span is finished.
     */
    public void setClientReceived() {

        Span currentSpan = spanAndEndpoint().span();
        if (currentSpan != null) {
            submitAnnotation(zipkinCoreConstants.CLIENT_RECV);
            spanCollector().collect(currentSpan);
            spanAndEndpoint().state().setCurrentClientSpan(null);
            spanAndEndpoint().state().setCurrentClientServiceName(null);
        }

    }

    /**
     * Start a new span for a new client request that will be bound to current thread. The ClientTracer can decide to return
     * <code>null</code> in case this request should not be traced (eg sampling).
     *
     * @param requestName Request name. Should not be <code>null</code> or empty.
     * @return Span id for new request or <code>null</code> in case we should not trace this new client request.
     */
    public SpanId startNewSpan(String requestName) {

        Boolean sample = spanAndEndpoint().state().sample();
        if (Boolean.FALSE.equals(sample)) {
            spanAndEndpoint().state().setCurrentClientSpan(null);
            spanAndEndpoint().state().setCurrentClientServiceName(null);
            return null;
        }

        SpanId newSpanId = getNewSpanId();
        if (sample == null) {
            // No sample indication is present.
            for (TraceFilter traceFilter : traceFilters()) {
                if (!traceFilter.trace(newSpanId.getSpanId(), requestName)) {
                    spanAndEndpoint().state().setCurrentClientSpan(null);
                    spanAndEndpoint().state().setCurrentClientServiceName(null);
                    return null;
                }
            }
        }

        Span newSpan = new Span();
        newSpan.setId(newSpanId.getSpanId());
        newSpan.setTrace_id(newSpanId.getTraceId());
        if (newSpanId.getParentSpanId() != null) {
            newSpan.setParent_id(newSpanId.getParentSpanId());
        }
        newSpan.setName(requestName);
        spanAndEndpoint().state().setCurrentClientSpan(newSpan);
        return newSpanId;
    }

    /**
     * Override the service name that will be submitted in the annotations.
     * <p/>
     * This should be set before submitting any annotations. So after invoking {@link ClientTracer#startNewSpan(String)} and
     * before {@link ClientTracer#setClientSent()}.
     *
     * @param serviceName should be the same as the name of the service the client is calling.
     */
    public void setCurrentClientServiceName(String serviceName) {
        spanAndEndpoint().state().setCurrentClientServiceName(serviceName);
    }

    private SpanId getNewSpanId() {

        Span currentServerSpan = spanAndEndpoint().state().getCurrentServerSpan().getSpan();
        long newSpanId = randomGenerator().nextLong();
        if (currentServerSpan == null) {
            return SpanId.create(newSpanId, newSpanId, null);
        }

        return SpanId.create(currentServerSpan.getTrace_id(), newSpanId, currentServerSpan.getId());
    }

    ClientTracer() {
    }
}
