package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.Random;
import zipkin.Constants;
import zipkin.reporter.Reporter;

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

    /** @deprecated Don't build your own ClientTracer. Use {@link Brave#clientTracer()} */
    @Deprecated
    public static Builder builder() {
        return new AutoValue_ClientTracer.Builder();
    }

    abstract CurrentSpan currentLocalSpan();
    abstract ServerSpanThreadBinder currentServerSpan();
    @Override abstract ClientSpanThreadBinder currentSpan();
    abstract SpanIdFactory spanIdFactory();

    /** @deprecated Don't build your own ClientTracer. Use {@link Brave#clientTracer()} */
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

        public final Builder state(ServerClientAndLocalSpanState state) {
            return endpoint(state.endpoint())
                .currentLocalSpan(new LocalSpanThreadBinder(state))
                .currentServerSpan(new ServerSpanThreadBinder(state))
                .currentSpan(new ClientSpanThreadBinder(state));
        }

        abstract Builder endpoint(Endpoint endpoint);

        abstract Builder currentLocalSpan(CurrentSpan currentLocalSpan);

        abstract Builder currentServerSpan(ServerSpanThreadBinder currentServerSpan);

        abstract Builder currentSpan(ClientSpanThreadBinder currentSpan);

        public abstract Builder reporter(Reporter<zipkin.Span> reporter);

        /**
         * @deprecated use {@link #reporter(Reporter)}
         */
        @Deprecated
        public final Builder spanCollector(SpanCollector spanCollector) {
            return reporter(new SpanCollectorReporterAdapter(spanCollector));
        }

        public abstract Builder clock(Clock clock);

        public abstract ClientTracer build();
    }

    /**
     * Sets 'client sent' event for current thread.
     */
    public void setClientSent() {
        submitStartAnnotation(Constants.CLIENT_SEND);
    }

    /**
     * Like {@link #setClientSent()}, except you can log the network context of the destination.
     *
     * @param server represents the server (peer). Set {@link Endpoint#service_name} to
     * "unknown" if unknown.
     */
    public void setClientSent(Endpoint server) {
        submitAddress(Constants.SERVER_ADDR, server);
        submitStartAnnotation(Constants.CLIENT_SEND);
    }

    /**
     * Like {@link #setClientSent()}, except you can log the network context of the destination.
     *
     * @param ipv4        ipv4 of the server as an int. Ex for 1.2.3.4, it would be (1 << 24) | (2 << 16) | (3 << 8) | 4
     * @param port        listen port the client is connecting to, or 0 if unknown
     * @param serviceName lowercase {@link Endpoint#service_name name} of the service being called
     *                    or null if unknown
     *
     * @deprecated use {@link #setClientSent(Endpoint)}
     */
    @Deprecated
    public void setClientSent(int ipv4, int port, @Nullable String serviceName) {
        if (serviceName == null) serviceName = "unknown";
        setClientSent(Endpoint.builder().ipv4(ipv4).port(port).serviceName(serviceName).build());
    }

    /**
     * Sets the 'client received' event for current thread. This will also submit span because setting a client received
     * event means this span is finished.
     */
    public void setClientReceived() {
        if (submitEndAnnotation(Constants.CLIENT_RECV)) {
            currentSpan().setCurrentSpan(null);
        }
    }

    /**
     * Start a new span for a new client request that will be bound to current thread. The ClientTracer can decide to return
     * <code>null</code> in case this request should not be traced (eg sampling).
     *
     * @param requestName Request name. Should be lowercase. Null or empty will defer to the server's name of the operation.
     * @return Span id for new request or <code>null</code> in case we should not trace this new client request.
     */
    public SpanId startNewSpan(@Nullable String requestName) {
        // When a trace context is extracted from an incoming request, it may have only the
        // sampled header (no ids). If the header says unsampled, we must honor that. Since
        // we currently don't synthesize a fake span when a trace is unsampled, we have to
        // check sampled state explicitly.
        Boolean sample = currentServerSpan().sampled();
        if (Boolean.FALSE.equals(sample)) {
            currentSpan().setCurrentSpan(null);
            return null;
        }

        SpanId nextContext = spanIdFactory().next(maybeParent());
        if (Boolean.FALSE.equals(nextContext.sampled())) {
            currentSpan().setCurrentSpan(null);
            return null;
        }

        Span newSpan = Span.create(nextContext).setName(requestName);
        currentSpan().setCurrentSpan(newSpan);
        return nextContext;
    }

    private SpanId maybeParent() {
        Span parentSpan = currentLocalSpan().get();
        if (parentSpan == null) {
            Span serverSpan = currentServerSpan().get();
            if (serverSpan != null) {
                parentSpan = serverSpan;
            }
        }
        if (parentSpan == null) return null;
        if (parentSpan.context() != null) return parentSpan.context();
        // If we got here, some implementation of state passed a deprecated span
        return SpanId.builder()
            .traceIdHigh(parentSpan.getTrace_id_high())
            .traceId(parentSpan.getTrace_id())
            .parentId(parentSpan.getParent_id())
            .spanId(parentSpan.getId()).build();
    }

    ClientTracer() {
    }
}
