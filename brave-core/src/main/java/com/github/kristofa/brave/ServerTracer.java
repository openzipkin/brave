package com.github.kristofa.brave;

import java.util.List;
import java.util.Random;

import com.github.kristofa.brave.SpanAndEndpoint.ServerSpanAndEndpoint;
import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;

import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.zipkinCoreConstants;

import static com.github.kristofa.brave.internal.Util.checkNotBlank;

/**
 * Used for setting up trace information for a request. When a request is received we typically do this:
 * <ol>
 * <li>Detect if we are part of existing trace/span. For example with services doing http requests this can be done by
 * detecting and getting values of http header that reresent trace/span ids.</li>
 * <li>Once detected we submit state using one of 3 following methods depending on the state we are in:
 * {@link ServerTracer#setStateCurrentTrace(long, long, Long, String), {@link ServerTracer#setStateNoTracing()} or
 * {@link ServerTracer#setStateUnknown(String)}.</li>
 * <li>Next we execute {@link ServerTracer#setServerReceived()} to mark the point in time at which we received the request.</li>
 * <li>Service request executes its logic...
 * <li>Just before sending response we execute {@link ServerTracer#setServerSend()}.
 * </ol>
 * 
 * @author kristof
 */
@AutoValue
public abstract class ServerTracer extends AnnotationSubmitter {

    public static Builder builder() {
        return new AutoValue_ServerTracer.Builder();
    }

    @Override
    abstract ServerSpanAndEndpoint spanAndEndpoint();
    abstract Random randomGenerator();
    abstract SpanCollector spanCollector();
    abstract List<TraceFilter> traceFilters();

    @AutoValue.Builder
    public abstract static class Builder {

        public Builder state(ServerSpanState state) {
            return spanAndEndpoint(ServerSpanAndEndpoint.create(state));
        }

        abstract Builder spanAndEndpoint(ServerSpanAndEndpoint spanAndEndpoint);

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

        abstract ServerTracer build();
    }

    /**
     * Clears current span.
     */
    public void clearCurrentSpan() {
        spanAndEndpoint().state().setCurrentServerSpan(null);
    }

    /**
     * Sets the current Trace/Span state. Using this method indicates we are part of an existing trace/span.
     *
     * @param traceId Trace id.
     * @param spanId Span id.
     * @param parentSpanId Parent span id. Can be <code>null</code>.
     * @param name Name should not be empty or <code>null</code>.
     * @see ServerTracer#setStateNoTracing()
     * @see ServerTracer#setStateUnknown(String)
     */
    public void setStateCurrentTrace(long traceId, long spanId, @Nullable Long parentSpanId, @Nullable String name) {
        checkNotBlank(name, "Null or blank span name");
        spanAndEndpoint().state().setCurrentServerSpan(
            ServerSpan.create(traceId, spanId, parentSpanId, name));
    }

    /**
     * Sets the current Trace/Span state. Using this method indicates that a parent request has decided that we should not
     * trace the current request.
     *
     * @see ServerTracer#setStateExistingTrace(TraceContext)
     * @see ServerTracer#setStateUnknown(String)
     */
    public void setStateNoTracing() {
        spanAndEndpoint().state().setCurrentServerSpan(ServerSpan.NOT_SAMPLED);
    }

    /**
     * Sets the current Trace/Span state. Using this method indicates that we got no information about being part of an
     * existing trace or about the fact that we should not trace the current request. In this case the ServerTracer will
     * decide what to do.
     *
     * @param spanName The name of our current request/span.
     */
    public void setStateUnknown(String spanName) {
        checkNotBlank(spanName, "Null or blank span name");
        for (TraceFilter traceFilter : traceFilters()) {
            if (traceFilter.trace(spanName) == false) {
                spanAndEndpoint().state().setCurrentServerSpan(ServerSpan.NOT_SAMPLED);
                return;
            }
        }
        long newSpanId = randomGenerator().nextLong();
        spanAndEndpoint().state().setCurrentServerSpan(
            ServerSpan.create(newSpanId, newSpanId, null, spanName));
    }

    /**
     * Sets server received event for current request. This should be done after setting state using one of 3 methods
     * {@link ServerTracer#setStateExistingTrace(TraceContext)}, {@link ServerTracer#setStateNoTracing()} or
     * {@link ServerTracer#setStateUnknown(String)}.
     */
    public void setServerReceived() {
        submitAnnotation(zipkinCoreConstants.SERVER_RECV);
    }

    /**
     * Sets the server sent event for current thread.
     */
    public void setServerSend() {
        Span currentSpan = spanAndEndpoint().state().getCurrentServerSpan().getSpan();
        if (currentSpan != null) {
            submitAnnotation(zipkinCoreConstants.SERVER_SEND);
            spanCollector().collect(currentSpan);
            spanAndEndpoint().state().setCurrentServerSpan(null);
        }
    }

    ServerTracer() {
    }
}
