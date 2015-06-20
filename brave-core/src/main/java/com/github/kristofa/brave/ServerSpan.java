package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Span;

/**
 * The ServerSpan is initialized by {@link ServerTracer} and keeps track of Trace/Span state of our service request.
 * 
 * @author adriaens
 */
public interface ServerSpan {

    /**
     * Gets the Trace/Span context.
     * 
     * @return Trace/Span context. Can be <code>null</code> in case we did not get any context in request.
     */
    Span getSpan();

    /**
     * Gets the sum of the durations of all threads that are executed as part of current service request.
     * 
     * @return the sum of the durations of all threads that are executed as part of current service request, in milliseconds.
     */
    long getThreadDuration();

    /**
     * Increment the thread duration for this service request.
     * 
     * @param durationMs Duration in milliseconds.
     */
    void incThreadDuration(final long durationMs);

    /**
     * Indicates if we need to sample this request or not.
     * 
     * @return <code>true</code> in case we should sample this request, <code>false</code> in case we should not sample this
     *         request or <code>null</code> in case we did not get any indication about sampling this request. In this case
     *         new client requests should decide about sampling or not.
     */
    Boolean getSample();

}
