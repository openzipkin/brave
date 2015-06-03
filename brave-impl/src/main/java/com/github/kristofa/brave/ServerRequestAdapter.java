package com.github.kristofa.brave;

import java.util.Optional;

/**
 * Provides properties needed for dealing with server request.
 *
 * @see ServerRequestInterceptor
 */
public interface ServerRequestAdapter {

    /**
     * Get the trace data from request.
     *
     * @return trace data.
     */
    TraceData getTraceData();

    /**
     * Gets the span name for request.
     * @return
     */
    String getSpanName();

    /**
     * Optional request representation. If specified it will be added to the span as a binary annotation 'request'.
     *
     * @return Optional request representation.
     */
    Optional<String> getRequestRepresentation();
}
