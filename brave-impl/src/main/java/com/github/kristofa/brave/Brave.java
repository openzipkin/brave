package com.github.kristofa.brave;

import java.util.Random;

/**
 * Public Brave api. Makes sure all returned instances share the same trace/span state.
 * <p>
 * This api should be used to create new instances for usage in your applications.
 * 
 * @author kristof
 */
public class Brave {

    private final static ServerAndClientSpanState SERVER_AND_CLIENT_SPAN_STATE = new ServerAndClientSpanStateImpl();
    private final static Random RANDOM_GENERATOR = new Random();

    private Brave() {
        // Only static access.
    }

    /**
     * Gets {@link EndPointSubmitter}. Allows you to set endpoint (ip, port, service name) for this service. Should be set
     * only once.
     * 
     * @return {@link EndPointSubmitter}.
     */
    public static EndPointSubmitter getEndPointSubmitter() {
        return new EndPointSubmitterImpl(SERVER_AND_CLIENT_SPAN_STATE);
    }

    /**
     * Gets a simple {@link SpanCollector} which logs spans through slf4j at info level.
     * 
     * @return A simple {@link SpanCollector} which logs spans through slf4j at info level.
     * @see Brave#getClientTracer(SpanCollector)
     * @see Brave#getServerTracer(SpanCollector)
     */
    public static SpanCollector getLoggingSpanCollector() {
        return new LoggingSpanCollectorImpl();
    }

    /**
     * Gets a {@link TraceFilter} that does not filtering at all.
     * 
     * @return TraceFilter that does not filtering at all.
     * @see Brave#getClientTracer(SpanCollector, TraceFilter)
     */
    public static TraceFilter getTraceAllTraceFilter() {
        return new TraceAllTraceFilter();
    }

    /**
     * Gets a {@link ClientTracer} that will be initialized with a custom {@link SpanCollector} and a custom
     * {@link TraceFilter}.
     * 
     * @param collector Custom {@link SpanCollector}. Should not be <code>null</code>.
     * @param traceFilter Custom trace filter. Should not be <code>null</code>.
     * @return {@link ClientTracer} instance.
     * @see Brave#getLoggingSpanCollector()
     * @see Brave#getTraceAllTraceFilter()
     */
    public static ClientTracer getClientTracer(final SpanCollector collector, final TraceFilter traceFilter) {
        return new ClientTracerImpl(SERVER_AND_CLIENT_SPAN_STATE, RANDOM_GENERATOR, collector, traceFilter);
    }

    /**
     * Gets a {@link ServerTracer}.
     * 
     * @param collector Custom {@link SpanCollector}. Should not be <code>null</code>.
     * @return {@link ServerTracer} instance.
     */
    public static ServerTracer getServerTracer(final SpanCollector collector) {
        return new ServerTracerImpl(SERVER_AND_CLIENT_SPAN_STATE, collector);
    }

    /**
     * Can be used to submit application specific annotations to the current server span.
     * 
     * @return Server span {@link AnnotationSubmitter}.
     */
    public static AnnotationSubmitter getServerSpanAnnotationSubmitter() {
        return new ServerTracerImpl(SERVER_AND_CLIENT_SPAN_STATE, new SpanCollector() {

            @Override
            public void collect(final Span span) {
                // Nothing.

            }
        });
    }

    /**
     * Only relevant if you start multiple threads in your server side code and you will use {@link ClientTracer},
     * {@link AnnotationSubmitter} from those threads.
     * 
     * @see ServerSpanThreadBinder
     * @return {@link ServerSpanThreadBinder}.
     */
    public static ServerSpanThreadBinder getServerSpanThreadBinder() {
        return new ServerSpanThreadBinderImpl(SERVER_AND_CLIENT_SPAN_STATE);
    }
}
