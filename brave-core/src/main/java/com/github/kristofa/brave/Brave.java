package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Util;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;

import static com.github.kristofa.brave.InetAddressUtilities.getLocalHostLANAddress;
import static com.github.kristofa.brave.InetAddressUtilities.toInt;

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
        private SpanCollector spanCollector = new LoggingSpanCollector();
        private Random random = new Random();
        // default added so callers don't need to check null.
        private Sampler sampler = Sampler.create(1.0f);

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
         * @param serviceName Name of service. Is only relevant when we do server side tracing.
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
         * @param serviceName Name of service. Is only relevant when we do server side tracing.
         */
        public Builder(int ip, int port, String serviceName) {
            state = new ThreadLocalServerClientAndLocalSpanState(ip, port, serviceName);
        }

        /**
         * Use for control of how tracing state propagates across threads.
         */
        public Builder(ServerClientAndLocalSpanState state) {
            this.state = Util.checkNotNull(state, "state must be specified.");
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
         * @param spanCollector
         */
        public Builder spanCollector(SpanCollector spanCollector) {
            this.spanCollector = spanCollector;
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
                .spanCollector(builder.spanCollector)
                .state(builder.state)
                .traceSampler(builder.sampler).build();

        clientTracer = ClientTracer.builder()
                .randomGenerator(builder.random)
                .spanCollector(builder.spanCollector)
                .state(builder.state)
                .traceSampler(builder.sampler).build();

        localTracer = LocalTracer.builder()
                .randomGenerator(builder.random)
                .spanCollector(builder.spanCollector)
                .spanAndEndpoint(SpanAndEndpoint.LocalSpanAndEndpoint.create(builder.state))
                .traceSampler(builder.sampler).build();
        
        serverRequestInterceptor = new ServerRequestInterceptor(serverTracer);
        serverResponseInterceptor = new ServerResponseInterceptor(serverTracer);
        clientRequestInterceptor = new ClientRequestInterceptor(clientTracer);
        clientResponseInterceptor = new ClientResponseInterceptor(clientTracer);
        serverSpanAnnotationSubmitter = AnnotationSubmitter.create(SpanAndEndpoint.ServerSpanAndEndpoint.create(builder.state));
        serverSpanThreadBinder = new ServerSpanThreadBinder(builder.state);
        clientSpanThreadBinder = new ClientSpanThreadBinder(builder.state);
    }
}
