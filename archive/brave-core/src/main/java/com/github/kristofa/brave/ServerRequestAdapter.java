package com.github.kristofa.brave;

import java.util.Collection;

/**
 * Provides properties needed for dealing with server request.
 *
 * @see ServerRequestInterceptor
 *
 * @deprecated Replaced by {@code HttpServerParser} from brave-http or {@code brave.SpanCustomizer}
 * if not http.
 */
@Deprecated
public interface ServerRequestAdapter {

    /**
     * Get the trace data from request.
     *
     * @return trace data.
     */
    TraceData getTraceData();

    /**
     * Gets the span name for request.
     *
     * @return Span name for request.
     */
    String getSpanName();

    /**
     * Returns a collection of annotations that should be added to span
     * for incoming request.
     *
     * Can be used to indicate more details about request next to span name.
     * For example for http requests an annotation containing the uri path could be added.
     *
     * @return Collection of annotations.
     */
    Collection<KeyValueAnnotation> requestAnnotations();
}
