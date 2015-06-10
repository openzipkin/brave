package com.github.kristofa.brave;

import java.util.Collection;

import javax.annotation.Nullable;

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
     * @param spanId Nullable span id. If null we don't need to trace request and you
     *               should pass an indication along with the request that indicates we won't trace this request.
     */
    void addSpanIdToRequest(@Nullable SpanId spanId);

    /**
     * Returns the service name for request. The service name is expected to be the same
     * as the service we are calling. The service name will be used in the endpoint that we
     * set when submitting annotations.
     *
     * @return service name.
     */
    String getClientServiceName();

    /**
     * Returns a collection of annotations that should be added to span
     * for given request.
     *
     * Can be used to indicate more details about request next to span name.
     * For example for http requests an annotation containing the uri path could be added.
     *
     * @return Collection of annotations.
     */
    Collection<KeyValueAnnotation> requestAnnotations();

}
