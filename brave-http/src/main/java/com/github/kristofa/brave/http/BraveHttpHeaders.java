package com.github.kristofa.brave.http;

/**
 * Contains the header keys that are used to represent trace id, span id, parent span id, sampled.
 * <p/>
 * The names correspond with the zipkin header values.
 * <p/>
 * These can be used to submit as HTTP header in a new request.
 *
 * <p>See https://github.com/openzipkin/b3-propagation for details
 * @author kristof
 */
public enum BraveHttpHeaders {

    /**
     * 128 or 64-bit trace ID lower-hex encoded into 32 or 16 characters (required)
     */
    TraceId("X-B3-TraceId"),
    /**
     * 64-bit span ID lower-hex encoded into 16 characters (required)
     */
    SpanId("X-B3-SpanId"),
    /**
     * 64-bit parent span ID lower-hex encoded into 16 characters (absent on root span)
     */
    ParentSpanId("X-B3-ParentSpanId"),
    /**
     * "1" means report this span to the tracing system, "0" means do not. (absent means defer the
     * decision to the receiver of this header).
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
