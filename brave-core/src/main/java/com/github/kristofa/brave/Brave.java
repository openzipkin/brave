package com.github.kristofa.brave;

import com.github.kristofa.brave.SpanAndEndpoint.ServerSpanAndEndpoint;

import java.util.List;
import java.util.Random;

/**
 * Public Brave api. Makes sure all returned instances share the same trace/span state.
 * <p>
 * This api should be used to create new instances for usage in your applications.
 * 
 * @author kristof
 */
public class Brave {

    private final static ServerAndClientSpanState SERVER_AND_CLIENT_SPAN_STATE = new ThreadLocalServerAndClientSpanState();
    private final static Random RANDOM_GENERATOR = new Random();

    private Brave() {
        // Only static access.
    }

    /**
     * Gets {@link EndpointSubmitter}. Allows you to set endpoint (ip, port, service name) for this service.
     * <p/>
     * Each annotation that is being submitted (including cs, cr, sr, ss) has an endpoint (host, port, service name)
     * assigned. For a given service/application instance the endpoint only needs to be set once and will be reused for all
     * submitted annotations.
     * <p/>
     * The Endpoint needs to be set using the EndpointSubmitter before any annotation/span is created.
     * 
     * @return {@link EndpointSubmitter}.
     */
    public static EndpointSubmitter getEndpointSubmitter() {
        return new EndpointSubmitter(SERVER_AND_CLIENT_SPAN_STATE);
    }

    /**
     * Gets a {@link ClientTracer} that will be initialized with a custom {@link SpanCollector} and a custom list of
     * {@link TraceFilter trace filters}.
     * <p/>
     * The ClientTracer is used to initiate a new span when doing a request to another service. It will generate the cs
     * (client send) and cr (client received) annotations. When the cr annotation is set the span will be submitted to
     * SpanCollector if not filtered by one of the trace filters.
     * 
     * @param collector Custom {@link SpanCollector}. Should not be <code>null</code>.
     * @param traceFilters List of Trace filters. List can be empty if you don't want trace filtering (sampling). The trace
     *            filters will be executed in order. If one returns false there will not be tracing and the next trace
     *            filters will not be executed anymore.
     * @return {@link ClientTracer} instance.
     * @see Brave#getLoggingSpanCollector()
     * @see Brave#getTraceAllTraceFilter()
     */
    public static ClientTracer getClientTracer(final SpanCollector collector, final List<TraceFilter> traceFilters) {
        return ClientTracer.builder()
            .state(SERVER_AND_CLIENT_SPAN_STATE)
            .randomGenerator(RANDOM_GENERATOR)
            .spanCollector(collector)
            .traceFilters(traceFilters)
            .build();
    }

    /**
     * Gets a {@link ServerTracer}.
     * <p/>
     * The ServerTracer is used to generate sr (server received) and ss (server send) annotations. When ss annotation is set
     * the span will be submitted to SpanCollector if our span needs to get traced (as decided by ClientTracer).
     * 
     * @param collector Custom {@link SpanCollector}. Should not be <code>null</code>.
     * @return {@link ServerTracer} instance.
     */
    public static ServerTracer getServerTracer(final SpanCollector collector, final List<TraceFilter> traceFilters) {
        return ServerTracer.builder()
            .state(SERVER_AND_CLIENT_SPAN_STATE)
            .randomGenerator(RANDOM_GENERATOR)
            .spanCollector(collector)
            .traceFilters(traceFilters)
            .build();
    }

    /**
     * Can be used to submit application specific annotations to the current server span.
     * 
     * @return Server span {@link AnnotationSubmitter}.
     */
    public static AnnotationSubmitter getServerSpanAnnotationSubmitter() {
        return AnnotationSubmitter.create(ServerSpanAndEndpoint.create(SERVER_AND_CLIENT_SPAN_STATE));
    }

    /**
     * Only relevant if you start multiple threads in your server side code and you will use {@link ClientTracer},
     * {@link AnnotationSubmitter} from those threads.
     * 
     * @see ServerSpanThreadBinder
     * @return {@link ServerSpanThreadBinder}.
     */
    public static ServerSpanThreadBinder getServerSpanThreadBinder() {
        return new ServerSpanThreadBinder(SERVER_AND_CLIENT_SPAN_STATE);
    }

    /**
     * Only relevant if you make async client call where the result is processed in the callback from a separate thread
     * and you will use {@link ClientTracer},
     *
     * @see ClientSpanThreadBinder
     * @return {@link ClientSpanThreadBinder}.
     */
    public static ClientSpanThreadBinder getClientSpanThreadBinder() {
        return new ClientSpanThreadBinder(SERVER_AND_CLIENT_SPAN_STATE);
    }
}
