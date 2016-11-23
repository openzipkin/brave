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
        } else {
            SpanId spanId = traceData.getSpanId();
            // We know an instrumented caller initiated the trace if they sampled it
            boolean clientOriginatedTrace = spanId != null && Boolean.TRUE.equals(sample);
            if (spanId != null) {
                // If the sampled flag was left unset, we need to make the decision here
                if (spanId.sampled() == null) {
                    spanId = spanId.toBuilder()
                        .sampled(serverTracer.traceSampler().isSampled(spanId.traceId))
                        .build();
                }
                if (!spanId.sampled()) {
                    LOGGER.fine("Received span information as part of request, but didn't sample.");
                    serverTracer.setStateNoTracing();
                } else {
                    LOGGER.fine("Received span information as part of request.");
                    serverTracer.setStateCurrentTrace(traceData.getSpanId(), adapter.getSpanName());
                }
            } else {
                LOGGER.fine("Received no span state.");
                serverTracer.setStateUnknown(adapter.getSpanName());
            }
            serverTracer.setServerReceived();
            // In the RPC span model, the client owns the timestamp and duration of the span. If we
            // were propagated an id, we can assume that we shouldn't report timestamp or duration,
            // rather let the client do that. Worst case we were propagated an unreported ID and
            // Zipkin backfills timestamp and duration.
            if (clientOriginatedTrace) {
                Span span = serverTracer.spanAndEndpoint().span();
                synchronized (span) {
                    span.setTimestamp(null);
                    span.startTick = null;
                }
            }
            for(KeyValueAnnotation annotation : adapter.requestAnnotations())
            {
                serverTracer.submitBinaryAnnotation(annotation.getKey(), annotation.getValue());
            }
        }
    }
}
