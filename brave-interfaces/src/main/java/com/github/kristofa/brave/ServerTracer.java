package com.github.kristofa.brave;

/**
 * Used for submitting server event information. Keeps state for each thread. </p> Depending on the implementation it can be
 * that each request is traced or it can be that only some requests are traced to avoid too much overhead. This is all
 * managed in the {@link ServerTracer} implementation. The user should not be aware.
 * 
 * @author kristof
 */
public interface ServerTracer extends AnnotationSubmitter {

    /**
     * Clears current span. When a thread pool is used this can be used to avoid you re-use previous information.
     */
    void clearCurrentSpan();

    /**
     * Sets the span we are part of. Using this method also indicates we will sample the request.
     * 
     * @param traceId Trace id.
     * @param spanId Span id.
     * @param parentSpanId Parent span id. Can be <code>null</code> if not parent span is available.
     * @param name Span name.
     */
    void setSpan(final long traceId, final long spanId, final Long parentSpanId, final String name);

    /**
     * Indicates that we should not sample current request.
     */
    void setNoSampling();

    /**
     * Sets server received event for current thread.
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
