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
 */
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
        SpanId context = traceData.getSpanId();
        if (context == null) {
            LOGGER.fine("Received no span state.");
            serverTracer.setStateUnknown(adapter.getSpanName());
            startServerSpan(adapter);
            return;
        }

        // We are now joining the span propagated to us, by re-using the trace and span ids here.
        context = serverTracer.spanIdFactory().join(context);

        // At this point, we have inherited a sampling decision or made one explicitly
        if (!context.sampled()) {
            LOGGER.fine("Received span information as part of request, but didn't sample.");
            serverTracer.setStateNoTracing();
        } else {
            LOGGER.fine("Received span information as part of request.");
            serverTracer.setStateCurrentTrace(context, adapter.getSpanName());
        }
        startServerSpan(adapter);

        // In the RPC span model, the client owns the timestamp and duration of the span. If we
        // were propagated an id, we can assume that we shouldn't report timestamp or duration,
        // rather let the client do that. Worst case we were propagated an unreported ID and
        // Zipkin backfills timestamp and duration.
        if (context.shared) {
            Span span = serverTracer.spanAndEndpoint().span();
            synchronized (span) {
                span.setTimestamp(null);
            }
        }
    }

    void startServerSpan(ServerRequestAdapter adapter) {
        serverTracer.setServerReceived();
        for(KeyValueAnnotation annotation : adapter.requestAnnotations())
        {
            serverTracer.submitBinaryAnnotation(annotation.getKey(), annotation.getValue());
        }
    }
}
