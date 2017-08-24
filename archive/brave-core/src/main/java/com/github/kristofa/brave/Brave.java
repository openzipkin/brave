package com.github.kristofa.brave;

import com.github.kristofa.brave.AnnotationSubmitter.Clock;
import com.github.kristofa.brave.AnnotationSubmitter.DefaultClock;
import com.github.kristofa.brave.internal.Internal;
import com.github.kristofa.brave.internal.InternalSpan;
import com.github.kristofa.brave.internal.Util;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import zipkin.Constants;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Reporter;
import zipkin.reporter.Sender;

import static com.github.kristofa.brave.InetAddressUtilities.getLocalHostLANAddress;
import static com.github.kristofa.brave.InetAddressUtilities.toInt;
import static zipkin.internal.Util.checkNotNull;

/** @deprecated Replaced by {@link brave.Tracing} */
@Deprecated
public class Brave {
    private final Clock clock;
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
        final Logger logger = Logger.getLogger(Brave.class.getName());
        final SpanFactory.Default.Builder spanFactoryBuilder = SpanFactory.Default.builder();
        private final ServerClientAndLocalSpanState state;
        final Endpoint localEndpoint;
        private boolean allowNestedLocalSpans = false;
        private Clock clock;
        private Recorder recorder;
        private SpanFactory spanFactory;
        private Reporter<zipkin.Span> reporter;

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
            int ipv4 = 127 << 24 | 1;
            try {
                ipv4 = toInt(getLocalHostLANAddress());
            } catch (UnknownHostException e) {
                logger.log(Level.WARNING, "Unable to get Inet address", e);
            }
            state = new ThreadLocalServerClientAndLocalSpanState(ipv4, 0, serviceName);
            localEndpoint = state.endpoint();
        }

        /**
         * Builder.
         *
         * @param ip          ipv4 host address as int. Ex for the ip 1.2.3.4, it would be (1 << 24) | (2 << 16) | (3 << 8) | 4
         * @param port        Port for service
         * @param serviceName Name of the local service being traced. Should be lowercase and not <code>null</code> or empty.
         */
        public Builder(int ip, int port, String serviceName) {
            this(Endpoint.builder().serviceName(serviceName).ipv4(ip).port(port).build());
        }

        /**
         * @param endpoint Endpoint of the local service being traced.
         */
        public Builder(Endpoint endpoint) {
            this(new ThreadLocalServerClientAndLocalSpanState(endpoint));
        }

        /**
         * Use for control of how tracing state propagates across threads.
         */
        public Builder(ServerClientAndLocalSpanState state) {
            this.state = Util.checkNotNull(state, "state must be specified.");
            this.localEndpoint = state.endpoint();

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
            this.spanFactoryBuilder.sampler(sampler);
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
         * reporter = AsyncReporter.create(URLConnectionSender.create("http://localhost:9411/api/v1/spans"));
         * braveBuilder.reporter(reporter);
         * }</pre>
         *
         * <p>See https://github.com/openzipkin/zipkin-reporter-java
         */
        public Builder reporter(Reporter<zipkin.Span> reporter) {
            this.reporter = checkNotNull(reporter, "reporter");
            return this;
        }

        /** Internal hook */
        Builder spanFactory(SpanFactory spanFactory) {
            this.spanFactory = spanFactory;
            return this;
        }

        /** Internal hook */
        Builder recorder(Recorder recorder) {
            this.recorder = recorder;
            return this;
        }

        /**
         * @deprecated use {@link #reporter(Reporter)}
         */
        @Deprecated
        public Builder spanCollector(SpanCollector spanCollector) {
            return reporter(new SpanCollectorReporterAdapter(spanCollector));
        }

        public Builder clock(Clock clock) {
            this.clock = checkNotNull(clock, "clock");
            return this;
        }

        /** When true, new root spans will have 128-bit trace IDs. Defaults to false (64-bit) */
        public Builder traceId128Bit(boolean traceId128Bit) {
            this.spanFactoryBuilder.traceId128Bit(traceId128Bit);
            return this;
        }

        public Brave build() {
            if (spanFactory == null) {
                spanFactory = spanFactoryBuilder.build();
            }

            if (clock == null) {
                clock = new DefaultClock();
            }

            if (reporter != null) {
                recorder = new AutoValue_Recorder_Default(localEndpoint, clock, reporter);
            } else if (recorder == null) {
                recorder =
                    new AutoValue_Recorder_Default(localEndpoint, clock, new LoggingReporter());
            }
            return new Brave(this);
        }

    }

    /**
     * Returns the clock that supplies epoch microsecond timestamps used for annotations.
     *
     * <p>This is exposed for collecting timestamps out-of-band for {@link LocalTracer}. Directly
     * modifying timestamps in {@link Span} is not supported and will break in Brave 4.
     *
     * @since 3.18
     */
    public Clock clock() {
        return clock;
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

    /** @deprecated Please use {@link #serverTracer()} instead. */
    @Deprecated
    public AnnotationSubmitter serverSpanAnnotationSubmitter() {
        return serverSpanAnnotationSubmitter;
    }

    private Brave(Builder builder) {
        clock = builder.clock;
        serverSpanThreadBinder = new ServerSpanThreadBinder(builder.state);
        clientSpanThreadBinder = new ClientSpanThreadBinder(builder.state);
        localSpanThreadBinder = new LocalSpanThreadBinder(builder.state);

        serverTracer = new AutoValue_ServerTracer(
            builder.recorder,
            serverSpanThreadBinder,
            builder.spanFactory
        );

        clientTracer = new AutoValue_ClientTracer(
            builder.recorder,
            localSpanThreadBinder,
            serverSpanThreadBinder,
            clientSpanThreadBinder,
            builder.spanFactory
        );

        localTracer = new AutoValue_LocalTracer.Builder()
                .spanFactory(builder.spanFactory)
                .recorder(builder.recorder)
                .allowNestedLocalSpans(builder.allowNestedLocalSpans)
                .currentServerSpan(serverSpanThreadBinder)
                .currentSpan(localSpanThreadBinder)
                .build();

        serverSpanAnnotationSubmitter = AnnotationSubmitter.create(
            serverSpanThreadBinder,
            builder.recorder
        );

        serverRequestInterceptor = new ServerRequestInterceptor(serverTracer);
        serverResponseInterceptor = new ServerResponseInterceptor(serverTracer);
        clientRequestInterceptor = new ClientRequestInterceptor(clientTracer);
        clientResponseInterceptor = new ClientResponseInterceptor(clientTracer);
    }

    static {
        Internal.instance = new Internal() {
            @Override public void setClientAddress(Brave brave, Endpoint ca) {
                Span span = brave.serverSpanThreadBinder().get();
                if (span == null) return;
                brave.serverTracer.recorder().address(span, Constants.CLIENT_ADDR, ca);
            }
        };
        new Span(); // ensure InternalSpan.instance points to a reference
    }

    /** Internal hook to create a new Span */
    static Span toSpan(SpanId context) {
        return InternalSpan.instance.toSpan(context);
    }

    /** Internal hook to retrieve the context associated with a Span */
    @Nullable static SpanId context(Span span) {
        return InternalSpan.instance.context(span);
    }
}
