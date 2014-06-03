package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;

/**
 * Maintains server span state.
 * 
 * @author kristof
 */
interface ServerSpanState extends CommonSpanState {

    /**
     * Gets the Span for the server request we are currently part of.
     * <p/>
     * Should be thread-aware to support multiple parallel requests.
     * 
     * @return Server request span for current thread. This will return the span we are part of. In case we should not trace
     *         current request <code>null</code> will be returned.
     */
    ServerSpan getCurrentServerSpan();

    /**
     * Gets the EndPoint (ip, port, service name) for this service.
     *
     * @return Endpoint for this service.
     */
    Endpoint getServerEndPoint();

    /**
     * Sets EndPoint for this service.
     *
     * @param endPoint EndPoint for this service.
     */
    void setServerEndPoint(final Endpoint endPoint);

    /**
     * Set span for current request.
     * 
     * @param span Span for current request.
     */
    void setCurrentServerSpan(final ServerSpan span);

    /**
     * Increment the duration of all threads being executed in this server span.
     * 
     * @param durationMs Duration in milliseconds.
     */
    public void incrementServerSpanThreadDuration(final long durationMs);

    /**
     * Gets the server span thread duration in milliseconds.
     * 
     * @return Server span thread duration in milliseconds.
     */
    public long getServerSpanThreadDuration();

}
