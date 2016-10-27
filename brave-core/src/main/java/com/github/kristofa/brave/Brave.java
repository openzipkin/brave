package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.Endpoint;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Reporter;
import zipkin.reporter.Sender;

import static com.github.kristofa.brave.InetAddressUtilities.getLocalHostLANAddress;
import static com.github.kristofa.brave.InetAddressUtilities.toInt;
import static zipkin.internal.Util.checkNotNull;

public class Brave {

    private final ServerTracer serverTracer;
    private final ClientTracer clientTracer;
    private final LocalTracer localTracer;
    private final ServerRequestInterceptor serverRequestInterceptor;
    private final ServerResponseInterceptor serverResponseInterceptor;
    private final ClientRequestInterceptor clientRequestInterceptor;
    private final ClientResponseInterceptor clientResponseInterceptor;
    private final AnnotationSubmitter serverSpanAnnotationSubmitter;
    private final ServerSpanThreadBinder serverSpanThreadBinder;
    private final ClientSpanThreadBinder clientSpanThreadBinder;
    private final LocalSpanThreadBinder localSpanThreadBinder;

    /**
     * Builds Brave api objects with following defaults if not overridden:
     * <p>
     * <ul>
     * <li>ThreadLocalServerClientAndLocalSpanState which binds trace/span state to current thread.</li>
     * <li>LoggingSpanCollector</li>
     * <li>Sampler that samples all traces</li>
     * </ul>
     */
    public static class Builder {

        private final ServerClientAndLocalSpanState state;
        private Reporter reporter = new LoggingReporter();
        private Random random = new Random();
        // default added so callers don't need to check null.
        private Sampler sampler = Sampler.create(1.0f);
        private boolean allowNestedLocalSpans = false;
        private AnnotationSubmitter.Clock clock = AnnotationSubmitter.DefaultClock.INSTANCE;
        private boolean traceId128Bit = false;

        /**
         * Builder which initializes with serviceName = "unknown".
         * <p>
         * When using this builder constructor we will try to 'guess' ip address by using java.net.* utility classes.
         * This might be convenient but not necessary what you want.
         * It is preferred to use constructor that takes ip, port and service name instead.
         * </p>
         */
        public Builder() {
            this("unknown");
        }

        /**
         * Builder.
         * <p>
         * When using this builder constructor we will try to 'guess' ip address by using java.net.* utility classes.
         * This might be convenient but not necessary what you want.
         * It is preferred to use constructor that takes ip, port and service name instead.
         * </p>
         *
         * @param serviceName Name of the local service being traced. Should be lowercase and not <code>null</code> or empty.
         */
        public Builder(String serviceName) {
            try {
                int ip = toInt(getLocalHostLANAddress());
                state = new ThreadLocalServerClientAndLocalSpanState(ip, 0, serviceName);
            } catch (UnknownHostException e) {
                throw new IllegalStateException("Unable to get Inet address", e);
            }
        }

        /**
         * Builder.
         *
         * @param ip          ipv4 host address as int. Ex for the ip 1.2.3.4, it would be (1 << 24) | (2 << 16) | (3 << 8) | 4
         * @param port        Port for service
         * @param serviceName Name of the local service being traced. Should be lowercase and not <code>null</code> or empty.
         */
        public Builder(int ip, int port, String serviceName) {
            state = new ThreadLocalServerClientAndLocalSpanState(ip, port, serviceName);
        }

        /**
         * @param endpoint Endpoint of the local service being traced.
         */
        public Builder(Endpoint endpoint) {
            state = new ThreadLocalServerClientAndLocalSpanState(endpoint);
        }

        /**
         * Use for control of how tracing state propagates across threads.
         */
        public Builder(ServerClientAndLocalSpanState state) {
            this.state = Util.checkNotNull(state, "state must be specified.");

            // the legacy span state doesn't support nested spans per (#166). Only permit nesting on the span
            // state that has instructions on how to use it properly
            this.allowNestedLocalSpans = state instanceof InheritableServerClientAndLocalSpanState;
        }

        /**
         * @deprecated use {@link #traceSampler(Sampler)} as filters here will be ignored.
         */
        @Deprecated
        public Builder traceFilters(List<TraceFilter> ignored) {
            return this; // noop
        }

        public Builder traceSampler(Sampler sampler) {
            this.sampler = sampler;
            return this;
        }

        /**
         * Controls how spans are reported. Defaults to logging, but often an {@link AsyncReporter}
         * which batches spans before sending to Zipkin.
         *
         * The {@link AsyncReporter} includes a {@link Sender}, which is a driver for transports
         * like http, kafka and scribe.
         *
         * <p>For example, here's how to batch send spans via http:
         *
         * <pre>{@code
         * reporter = AsyncReporter.builder(URLConnectionSender.create("http://localhost:9411/api/v1/spans"))
         *                         .build();
         *
         * braveBuilder.reporter(reporter);
         * }</pre>
         *
         * <p>See https://github.com/openzipkin/zipkin-reporter-java
         */
        public Builder reporter(Reporter<zipkin.Span> reporter) {
            this.reporter = checkNotNull(reporter, "reporter");
            return this;
        }

        /**
         * @deprecated use {@link #reporter(Reporter)}
         */
        @Deprecated
        public Builder spanCollector(SpanCollector spanCollector) {
            this.reporter = new SpanCollectorReporterAdapter(spanCollector);
            return this;
        }

        public Builder clock(AnnotationSubmitter.Clock clock) {
            this.clock = clock;
            return this;
        }

        /** When true, new root spans will have 128-bit trace IDs. Defaults to false (64-bit) */
        public Builder traceId128Bit(boolean traceId128Bit) {
            this.traceId128Bit = traceId128Bit;
            return this;
        }

        public Brave build() {
            return new Brave(this);
        }

    }

    /**
     * Client Tracer.
     * <p>
     * It is advised that you use ClientRequestInterceptor and ClientResponseInterceptor instead.
     * Those api's build upon ClientTracer and have a higher level api.
     * </p>
     *
     * @return ClientTracer implementation.
     */
    public ClientTracer clientTracer() {
        return clientTracer;
    }

    /**
     * Returns a tracer used to log in-process activity.
     *
     * @since 3.2
     */
    public LocalTracer localTracer() {
        return localTracer;
    }

    /**
     * Server Tracer.
     * <p>
     * It is advised that you use ServerRequestInterceptor and ServerResponseInterceptor instead.
     * Those api's build upon ServerTracer and have a higher level api.
     * </p>
     *
     * @return ClientTracer implementation.
     */
    public ServerTracer serverTracer() {
        return serverTracer;
    }

    public ClientRequestInterceptor clientRequestInterceptor() {
        return clientRequestInterceptor;
    }

    public ClientResponseInterceptor clientResponseInterceptor() {
        return clientResponseInterceptor;
    }

    public ServerRequestInterceptor serverRequestInterceptor() {
        return serverRequestInterceptor;
    }

    public ServerResponseInterceptor serverResponseInterceptor() {
        return serverResponseInterceptor;
    }

    /**
     * Helper object that can be used to propogate server trace state. Typically over different threads.
     *
     * @return {@link ServerSpanThreadBinder}.
     * @see ServerSpanThreadBinder
     */
    public ServerSpanThreadBinder serverSpanThreadBinder() {
        return serverSpanThreadBinder;
    }

    /**
     * Helper object that can be used to propagate client trace state. Typically over different threads.
     *
     * @return {@link ClientSpanThreadBinder}.
     * @see ClientSpanThreadBinder
     */
    public ClientSpanThreadBinder clientSpanThreadBinder() {
        return clientSpanThreadBinder;
    }

    /**
     * Helper object that can be used to propagate local trace state. Typically over different
     * threads.
     *
     * @return {@link LocalSpanThreadBinder}.
     * @see LocalSpanThreadBinder
     */
    public LocalSpanThreadBinder localSpanThreadBinder() {
        return localSpanThreadBinder;
    }

    /**
     * Can be used to submit application specific annotations to the current server span.
     *
     * @return Server span {@link AnnotationSubmitter}.
     */
    public AnnotationSubmitter serverSpanAnnotationSubmitter() {
        return serverSpanAnnotationSubmitter;
    }

    private Brave(Builder builder) {
        serverTracer = ServerTracer.builder()
                .randomGenerator(builder.random)
                .reporter(builder.reporter)
                .state(builder.state)
                .traceSampler(builder.sampler)
                .clock(builder.clock)
                .traceId128Bit(builder.traceId128Bit)
                .build();

        clientTracer = ClientTracer.builder()
                .randomGenerator(builder.random)
                .reporter(builder.reporter)
                .state(builder.state)
                .traceSampler(builder.sampler)
                .clock(builder.clock)
                .traceId128Bit(builder.traceId128Bit)
                .build();

        localTracer = LocalTracer.builder()
                .randomGenerator(builder.random)
                .reporter(builder.reporter)
                .allowNestedLocalSpans(builder.allowNestedLocalSpans)
                .spanAndEndpoint(SpanAndEndpoint.LocalSpanAndEndpoint.create(builder.state))
                .traceSampler(builder.sampler)
                .clock(builder.clock)
                .traceId128Bit(builder.traceId128Bit)
                .build();

        serverRequestInterceptor = new ServerRequestInterceptor(serverTracer);
        serverResponseInterceptor = new ServerResponseInterceptor(serverTracer);
        clientRequestInterceptor = new ClientRequestInterceptor(clientTracer);
        clientResponseInterceptor = new ClientResponseInterceptor(clientTracer);
        serverSpanAnnotationSubmitter = AnnotationSubmitter.create(SpanAndEndpoint.ServerSpanAndEndpoint.create(builder.state));
        serverSpanThreadBinder = new ServerSpanThreadBinder(builder.state);
        clientSpanThreadBinder = new ClientSpanThreadBinder(builder.state);
        localSpanThreadBinder = new LocalSpanThreadBinder(builder.state);
    }
}
