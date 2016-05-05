package com.github.kristofa.brave;

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
        if (sample != null && Boolean.FALSE.equals(sample)) {
            serverTracer.setStateNoTracing();
            LOGGER.fine("Received indication that we should NOT trace.");
        } else {
            if (traceData.getSpanId() != null) {
                LOGGER.fine("Received span information as part of request.");
                SpanId spanId = traceData.getSpanId();
                serverTracer.setStateCurrentTrace(spanId.traceId, spanId.spanId,
                        spanId.nullableParentId(), adapter.getSpanName());
            } else {
                LOGGER.fine("Received no span state.");
                serverTracer.setStateUnknown(adapter.getSpanName());
            }
            serverTracer.setServerReceived();
            for(KeyValueAnnotation annotation : adapter.requestAnnotations())
            {
                serverTracer.submitBinaryAnnotation(annotation.getKey(), annotation.getValue());
            }
        }
    }
}
