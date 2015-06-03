package com.github.kristofa.brave;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Contains logic for handling an incoming server request.
 *
 * @see ServerRequestAdapter
 */
public class ServerRequestInterceptor {

    private final static Logger LOGGER = LoggerFactory.getLogger(ServerRequestInterceptor.class);
    private final static String SERVER_REQUEST_ANNOTATION_NAME = "request";

    private final ServerTracer serverTracer;

    public ServerRequestInterceptor(ServerTracer serverTracer) {
        this.serverTracer = Objects.requireNonNull(serverTracer, "serverTracer should not be null.");
    }

    /**
     * Handles incoming request.
     *
     * @param adapter The adapter translates implementation specific details.
     */
    public void handle(ServerRequestAdapter adapter) {
        serverTracer.clearCurrentSpan();
        final TraceData traceData = adapter.getTraceData();

        Optional<Boolean> sample = traceData.getSample();
        if (sample.isPresent() && Boolean.FALSE.equals(sample.get())) {
            serverTracer.setStateNoTracing();
            LOGGER.debug("Received indication that we should NOT trace.");
        } else {
            if (traceData.getSpanId().isPresent()) {
                LOGGER.debug("Received span information as part of request.");
                SpanId spanId = traceData.getSpanId().get();
                serverTracer.setStateCurrentTrace(spanId.getTraceId(), spanId.getSpanId(),
                        spanId.getOptionalParentSpanId().orElse(null), adapter.getSpanName());
            } else {
                LOGGER.debug("Received no span state.");
                serverTracer.setStateUnknown(adapter.getSpanName());
            }
            serverTracer.setServerReceived();
            if (adapter.getRequestRepresentation().isPresent()) {
                serverTracer.submitBinaryAnnotation(SERVER_REQUEST_ANNOTATION_NAME, adapter.getRequestRepresentation().get());
            }
        }
    }
}
