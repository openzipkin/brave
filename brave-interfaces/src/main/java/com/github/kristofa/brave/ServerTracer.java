package com.github.kristofa.brave;

/**
 * Used for setting up trace information for a request. When a request is received we typically do this:
 * <ol>
 * <li>Detect if we are part of existing trace/span. For example with services doing http requests this can be done by
 * detecting and getting values of http header that reresent trace/span ids.</li>
 * <li>Once detected we submit state using one of 3 following methods depending on the state we are in:
 * {@link ServerTracer#setStateExistingTrace(TraceContext)}, {@link ServerTracer#setStateNoTracing()} or
 * {@link ServerTracer#setStateUnknown(String)}.</li>
 * <li>Next we execute {@link ServerTracer#setServerReceived()} to mark the point in time at which we received the request.</li>
 * <li>Service request executes its logic...
 * <li>Just before sending response we execute {@link ServerTracer#setServerSend()}.
 * </ol>
 * 
 * @author kristof
 */
public interface ServerTracer extends AnnotationSubmitter {

    /**
     * Clears current span.
     */
    void clearCurrentSpan();

    /**
     * Sets the current Trace/Span state. Using this method indicates we are part of an existing trace/span.
     * 
     * @param traceId Trace id.
     * @param spanId Span id.
     * @param parentSpanId Parent span id. Can be <code>null</code>.
     * @param name Name should not be empty or <code>null</code>.
     * @see ServerTracer#setStateNoTracing()
     * @see ServerTracer#setStateUnknown(String)
     */
    void setStateCurrentTrace(final long traceId, final long spanId, final Long parentSpanId, final String name);

    /**
     * Sets the current Trace/Span state. Using this method indicates that a parent request has decided that we should not
     * trace the current request.
     * 
     * @see ServerTracer#setStateExistingTrace(TraceContext)
     * @see ServerTracer#setStateUnknown(String)
     */
    void setStateNoTracing();

    /**
     * Sets the current Trace/Span state. Using this method indicates that we got no information about being part of an
     * existing trace or about the fact that we should not trace the current request. In this case the ServerTracer will
     * decide what to do.
     * 
     * @param spanName The name of our current request/span.
     */
    void setStateUnknown(final String spanName);

    /**
     * Sets server received event for current request. This should be done after setting state using one of 3 methods
     * {@link ServerTracer#setStateExistingTrace(TraceContext)}, {@link ServerTracer#setStateNoTracing()} or
     * {@link ServerTracer#setStateUnknown(String)}.
     */
    void setServerReceived();

    /**
     * Sets the server sent event for current thread.
     */
    void setServerSend();

    /**
     * Gets the thread execution duration for this span.
     * 
     * @return Thread execution duration for this span.
     */
    long getThreadDuration();

}
