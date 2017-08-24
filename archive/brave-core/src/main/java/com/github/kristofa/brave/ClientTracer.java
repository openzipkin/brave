package com.github.kristofa.brave;

import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.util.Random;
import javax.annotation.Nullable;
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
 * @deprecated Replaced by {@code brave.Span} with kind=Kind.CLIENT
 */
@Deprecated
@AutoValue
public abstract class ClientTracer extends AnnotationSubmitter {

    /** @deprecated Don't build your own ClientTracer. Use {@link Brave#clientTracer()} */
    @Deprecated
    public static Builder builder() {
        return new Builder();
    }

    abstract CurrentSpan currentLocalSpan();
    abstract ServerSpanThreadBinder currentServerSpan();
    @Override abstract ClientSpanThreadBinder currentSpan();
    abstract SpanFactory spanFactory();

    /** @deprecated Don't build your own ClientTracer. Use {@link Brave#clientTracer()} */
    @Deprecated
    public final static class Builder {
        final SpanFactory.Default.Builder spanFactoryBuilder = SpanFactory.Default.builder();
        Endpoint localEndpoint;
        CurrentSpan currentLocalSpan;
        ServerSpanThreadBinder currentServerSpan;
        ClientSpanThreadBinder currentSpan;
        Reporter reporter;
        Clock clock;

        /** Used to generate new trace/span ids. */
        public final Builder randomGenerator(Random randomGenerator) {
            spanFactoryBuilder.randomGenerator(randomGenerator);
            return this;
        }

        public final Builder traceSampler(Sampler sampler) {
            spanFactoryBuilder.sampler(sampler);
            return this;
        }

        public final Builder state(ServerClientAndLocalSpanState state) {
            this.currentLocalSpan = new LocalSpanThreadBinder(state);
            this.currentServerSpan = new ServerSpanThreadBinder(state);
            this.currentSpan = new ClientSpanThreadBinder(state);
            this.localEndpoint = state.endpoint();
            return this;
        }

        public final Builder reporter(Reporter<zipkin.Span> reporter) {
            this.reporter = reporter;
            return this;
        }

        /** @deprecated use {@link #reporter(Reporter)} */
        @Deprecated
        public final Builder spanCollector(SpanCollector spanCollector) {
            return reporter(new SpanCollectorReporterAdapter(spanCollector));
        }

        public final Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public final ClientTracer build() {
            return new AutoValue_ClientTracer(
                new AutoValue_Recorder_Default(localEndpoint, clock, reporter),
                currentLocalSpan,
                currentServerSpan,
                currentSpan,
                spanFactoryBuilder.build()
            );
        }

        Builder() { // intentionally package private
        }
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

        Span newSpan = spanFactory().nextSpan(maybeParent());
        SpanId nextContext = Brave.context(newSpan);
        if (Boolean.FALSE.equals(nextContext.sampled())) {
            currentSpan().setCurrentSpan(null);
            return null;
        }

        recorder().name(newSpan, requestName);
        currentSpan().setCurrentSpan(newSpan);
        return nextContext;
    }

    @Nullable SpanId maybeParent() {
        Span parentSpan = currentLocalSpan().get();
        if (parentSpan == null) {
            Span serverSpan = currentServerSpan().get();
            if (serverSpan != null) {
                parentSpan = serverSpan;
            }
        }
        if (parentSpan == null) return null;
        return Brave.context(parentSpan);
    }

    ClientTracer() {
    }
}
