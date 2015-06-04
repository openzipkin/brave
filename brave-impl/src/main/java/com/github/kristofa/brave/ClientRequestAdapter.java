package com.github.kristofa.brave;


import java.util.Optional;

public interface ClientRequestAdapter {

    /**
     * Gets the span name for request.
     *
     * @return Span name for request.
     */
    String getSpanName();

    /**
     * Enrich the request with the Spanid so we pass the state to the
     * service we are calling.
     *
     * @param spanId Optional span id. If empty we don't need to trace request and you
     *               should pass an indication along with the request that indicates we won't trace this request.
     */
    void addSpanIdToRequest(Optional<SpanId> spanId);

    /**
     * Returns the service name for request. The service name is expected to be the same
     * as the service we are calling. The service name will be used in the endpoint that we
     * set when submitting annotations.
     *
     * @return service name.
     */
    String getClientServiceName();

    /**
     * Optional request representation. If specified it will be added to the span as a binary annotation 'request'.
     *
     * @return Optional request representation.
     */
    Optional<String> getRequestRepresentation();
}
