package com.github.kristofa.brave;

/**
 * Contains the header keys that are used to represent trace id, span id, parent span id, sampled.
 * <p/>
 * The names correspond with the zipkin header values.
 * <p/>
 * These can be used to submit as HTTP header in a new request.
 * 
 * @author kristof
 */
public enum BraveHttpHeaders {

    /**
     * Trace id http header field name.
     */
    TraceId("X-B3-TraceId"),
    /**
     * Span id http header field name.
     */
    SpanId("X-B3-SpanId"),
    /**
     * Parent span id http header field name.
     */
    ParentSpanId("X-B3-ParentSpanId"),
    /**
     * Sampled http header field name. Indicates if this trace should be sampled or not.
     */
    Sampled("X-B3-Sampled"),
    /**
     * Span name as submitted by client.
     * <p/>
     * By default we will use the URL as span name but when we want to do grouping and aggregation of service requests using
     * URL is not always a good idea as it might contain variable parameters that make it difficult to match same service
     * requests. Providing a separate name for it can help.
     */
    SpanName("X-B3-SpanName");

    private final String name;

    BraveHttpHeaders(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
