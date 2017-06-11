package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;
import java.util.logging.Logger;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Contains logic for handling an incoming server request.
 *
 * - Get trace state from request
 * - Set state for current request
 * - Submit `Server Received` annotation
 *
 * @see ServerRequestAdapter
 * @deprecated Replaced by {@code HttpServerHander} from brave-instrumentation-http
 */
@Deprecated
public class ServerRequestInterceptor {

    private final static Logger LOGGER = Logger.getLogger(ServerRequestInterceptor.class.getName());

    private final ServerTracer serverTracer;

    public ServerRequestInterceptor(ServerTracer serverTracer) {
        this.serverTracer = checkNotNull(serverTracer, "Null serverTracer");
    }

    /**
     * Handles incoming request.
     *
     * @param adapter The adapter translates implementation specific details.
     */
    public void handle(ServerRequestAdapter adapter) {
        serverTracer.clearCurrentSpan();
        final TraceData traceData = adapter.getTraceData();

        Boolean sample = traceData.getSample();
        if (Boolean.FALSE.equals(sample)) {
            serverTracer.setStateNoTracing();
            LOGGER.fine("Received indication that we should NOT trace.");
            return;
        }

        Span span;
        if (traceData.getSpanId() != null) {
            LOGGER.fine("Received span information as part of request.");
            // We are now joining the span propagated to us, by re-using the trace and span ids here.
            span = serverTracer.spanFactory().joinSpan(traceData.getSpanId());
        } else {
            LOGGER.fine("Received no span state.");
            span = serverTracer.spanFactory().nextSpan(null);
        }
        SpanId context = Brave.context(span);

        // At this point, we have inherited a sampling decision or made one explicitly via join
        if (!context.sampled()) {
            LOGGER.fine("Trace is unsampled.");
            serverTracer.setStateNoTracing();
            return;
        }
        // Associate the span with the thread context as all serverTracer methods look that up
        serverTracer.setStateCurrentTrace(span,  adapter.getSpanName());

        serverTracer.setServerReceived();
        for(KeyValueAnnotation annotation : adapter.requestAnnotations())
        {
            serverTracer.submitBinaryAnnotation(annotation.getKey(), annotation.getValue());
        }
    }
}
