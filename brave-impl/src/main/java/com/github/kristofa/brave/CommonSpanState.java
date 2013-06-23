package com.github.kristofa.brave;

import com.twitter.zipkin.gen.Endpoint;

/**
 * Keeps track of common trace/span state information.
 * <p>
 * Should be thread aware since we can have multiple parallel request which means multiple trace/spans.
 * </p>
 * 
 * @author kristof
 */
interface CommonSpanState {

    /**
     * Indicates if we should trace current request.
     * <p/>
     * Should be thread-aware to support multiple parallel requests.
     * 
     * @return <code>true</code> in case we should trace current request, <code>false</code> in case we should not trace
     *         current request.
     */
    boolean shouldTrace();

    /**
     * Indicates if we should trace current request. If one of the callers decided that this trace should not be traced it
     * should have submitted it via header information.
     * 
     * @param shouldTrace <code>true</code> in case we should trace this request. <code>false</code> in case we should not
     *            trace the request. The default value is <code>true</code>.
     */
    void setTracing(final boolean shouldTrace);

    /**
     * Gets the EndPoint (ip, port, service name) for this service.
     * 
     * @return Endpoint for this service.
     */
    Endpoint getEndPoint();

    /**
     * Sets EndPoint for this service.
     * 
     * @param endPoint EndPoint for this service.
     */
    void setEndPoint(final Endpoint endPoint);

}
