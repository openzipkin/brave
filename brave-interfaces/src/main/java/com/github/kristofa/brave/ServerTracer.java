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
     * Sets the span we are part of.
     * 
     * @param traceId Trace id.
     * @param spanId Span id.
     * @param parentSpanId Parent span id. Can be <code>null</code> if not parent span is available.
     * @param name Span name.
     */
    void setSpan(final long traceId, final long spanId, final Long parentSpanId, final String name);

    /**
     * Sets indication if we should trace the current request.
     * 
     * @param shouldTrace <code>true</code> in case we should trace current request. <code>false</code> in case we should not
     *            trace current request.
     */
    void setShouldTrace(final boolean shouldTrace);

    /**
     * Sets server received event for current thread.
     */
    void setServerReceived();

    /**
     * Sets the server sent event for current thread.
     */
    void setServerSend();

}
