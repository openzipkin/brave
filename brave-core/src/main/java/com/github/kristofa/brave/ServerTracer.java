package com.github.kristofa.brave;

import com.github.kristofa.brave.SpanAndEndpoint.ServerSpanAndEndpoint;
import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Endpoint;
import java.util.Random;
import zipkin.Constants;
import zipkin.reporter.Reporter;

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

    /** @deprecated Don't build your own ServerTracer. Use {@link Brave#serverTracer()} */
    @Deprecated
    public static Builder builder() {
        return new AutoValue_ServerTracer.Builder();
    }

    @Override
    abstract ServerSpanAndEndpoint spanAndEndpoint();
    abstract SpanIdFactory spanIdFactory();
    abstract Reporter<zipkin.Span> reporter();

    /** @deprecated Don't build your own ServerTracer. Use {@link Brave#serverTracer()} */
    @Deprecated
    @AutoValue.Builder
    public abstract static class Builder {
        abstract Builder spanIdFactory(SpanIdFactory spanIdFactory);

        abstract SpanIdFactory.Builder spanIdFactoryBuilder();

        /** Used to generate new trace/span ids. */
        public final Builder randomGenerator(Random randomGenerator) {
            spanIdFactoryBuilder().randomGenerator(randomGenerator);
            return this;
        }

        public final Builder traceSampler(Sampler sampler) {
            spanIdFactoryBuilder().sampler(sampler);
            return this;
        }

        public Builder state(ServerSpanState state) {
            return spanAndEndpoint(ServerSpanAndEndpoint.create(state));
        }

        abstract Builder spanAndEndpoint(ServerSpanAndEndpoint spanAndEndpoint);

        public abstract Builder reporter(Reporter<zipkin.Span> reporter);

        /**
         * @deprecated use {@link #reporter(Reporter)}
         */
        @Deprecated
        public final Builder spanCollector(SpanCollector spanCollector) {
            return reporter(new SpanCollectorReporterAdapter(spanCollector));
        }

        public abstract Builder clock(Clock clock);

        public abstract ServerTracer build();
    }

    /**
     * Clears current span.
     */
    public void clearCurrentSpan() {
        spanAndEndpoint().state().setCurrentServerSpan(null);
    }

    /**
     * @deprecated since 3.15 use {@link #setStateCurrentTrace(SpanId, String)}
     */
    @Deprecated
    public void setStateCurrentTrace(long traceId, long spanId, @Nullable Long parentSpanId, String name) {
        SpanId context = SpanId.builder().traceId(traceId).spanId(spanId).parentId(parentSpanId).build();
        setStateCurrentTrace(context, name);
    }

    /**
     * Sets the current Trace/Span state. Using this method indicates we are part of an existing trace/span.
     *
     * @param context includes the trace identifiers extracted from the wire
     * @param spanName should not be empty or <code>null</code>.
     * @see ServerTracer#setStateNoTracing()
     * @see ServerTracer#setStateUnknown(String)
     */
    public void setStateCurrentTrace(SpanId context, String spanName) {
        checkNotBlank(spanName, "Null or blank span name");
        ServerSpan span = ServerSpan.create(context, spanName);
        spanAndEndpoint().state().setCurrentServerSpan(span);
    }

    /**
     * Sets the current Trace/Span state. Using this method indicates that a parent request has decided that we should not
     * trace the current request.
     *
     * @see ServerTracer#setStateCurrentTrace(SpanId, String)
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
        SpanId nextContext = spanIdFactory().next(null);
        if (Boolean.FALSE.equals(nextContext.sampled())) {
            spanAndEndpoint().state().setCurrentServerSpan(ServerSpan.NOT_SAMPLED);
            return;
        }

        setStateCurrentTrace(nextContext, spanName);
    }

    /**
     * Sets server received event for current request. This should be done after setting state using one of 3 methods
     * {@link ServerTracer#setStateCurrentTrace(SpanId, String)} , {@link ServerTracer#setStateNoTracing()} or
     * {@link ServerTracer#setStateUnknown(String)}.
     */
    public void setServerReceived() {
        submitStartAnnotation(Constants.SERVER_RECV);
    }

    /**
     * Like {@link #setServerReceived()}, except you can log the network context of the caller, for
     * example an IP address from the {@code X-Forwarded-For} header.
     *
     * @param client represents the client (peer). Set {@link Endpoint#service_name} to
     * "unknown" if unknown.
     */
    public void setServerReceived(Endpoint client) {
        submitAddress(Constants.CLIENT_ADDR, client);
        submitStartAnnotation(Constants.SERVER_RECV);
    }

    /**
     * Like {@link #setServerReceived()}, except you can log the network context of the caller, for
     * example an IP address from the {@code X-Forwarded-For} header.
     *
     * @param ipv4          ipv4 of the client as an int. Ex for 1.2.3.4, it would be (1 << 24) | (2 << 16) | (3 << 8) | 4
     * @param port          port for client-side of the socket, or 0 if unknown
     * @param clientService lowercase {@link Endpoint#service_name name} of the callee service or
     *                      null if unknown
     *
     * @deprecated use {@link #setServerReceived(Endpoint)}
     */
    @Deprecated
    public void setServerReceived(int ipv4, int port, @Nullable String clientService) {
        if (clientService == null) clientService = "unknown";
        setServerReceived(Endpoint.builder().ipv4(ipv4).port(port).serviceName(clientService).build());
    }

    /**
     * Sets the server sent event for current thread.
     */
    public void setServerSend() {
        if (submitEndAnnotation(Constants.SERVER_SEND)) {
            spanAndEndpoint().state().setCurrentServerSpan(null);
        }
    }

    ServerTracer() {
    }
}
