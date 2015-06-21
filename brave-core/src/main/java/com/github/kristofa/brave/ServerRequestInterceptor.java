package com.github.kristofa.brave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Contains logic for handling an incoming server request.
 *
 * @see ServerRequestAdapter
 */
public class ServerRequestInterceptor {

    private final static Logger LOGGER = LoggerFactory.getLogger(ServerRequestInterceptor.class);

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
            LOGGER.debug("Received indication that we should NOT trace.");
        } else {
            if (traceData.getSpanId() != null) {
                LOGGER.debug("Received span information as part of request.");
                SpanId spanId = traceData.getSpanId();
                serverTracer.setStateCurrentTrace(spanId.getTraceId(), spanId.getSpanId(),
                        spanId.getParentSpanId(), adapter.getSpanName());
            } else {
                LOGGER.debug("Received no span state.");
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
