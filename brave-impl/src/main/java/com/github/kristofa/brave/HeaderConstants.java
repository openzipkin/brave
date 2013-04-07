package com.github.kristofa.brave;

/**
 * Contains the header keys that are used to represent trace id, span id, parent span id, sampled.
 * <p/>
 * The names correspond with the zipkin header values.
 * 
 * @author kristof
 */
public class HeaderConstants {

    /**
     * Trace id header key.
     */
    public final static String TRACE_ID = "X-B3-TraceId";

    /**
     * Span id header key.
     */
    public final static String SPAN_ID = "X-B3-SpanId";

    /**
     * Parent span id header key.
     */
    public final static String PARENT_SPAN_ID = "X-B3-ParentSpanId";

    /**
     * Sampled header key. Indicates if we should trace this request or not.
     */
    public final static String SHOULD_GET_TRACED = "X-B3-Sampled";

}
