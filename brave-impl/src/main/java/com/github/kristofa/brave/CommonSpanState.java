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
     * Indicates if we should sample current request.
     * <p/>
     * Should be thread-aware to support multiple parallel requests.
     * 
     * @return <code>null</code> in case there is no indication if we should sample or not. <code>true</code> in case we
     *         should got the indication we should sample current request, <code>false</code> in case we should not sample
     *         the current request.
     */
    Boolean sample();

    /**
     * Indicates if we should sample current request. If one of the clients decides that this request should or should not be
     * sampled it is submitted it via header information.
     * 
     * @param sample <code>true</code> in case we should sample this request. <code>false</code> in case we should not sample
     *            the request. <code>null</code> in case we have no indication if we should sample this request.
     */
    void setSample(final Boolean sample);

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
