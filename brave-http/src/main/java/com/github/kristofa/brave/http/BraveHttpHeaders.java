package com.github.kristofa.brave.http;

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
    Sampled("X-B3-Sampled");

    private final String name;

    BraveHttpHeaders(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
