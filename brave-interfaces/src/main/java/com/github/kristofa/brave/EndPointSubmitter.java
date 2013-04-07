package com.github.kristofa.brave;

/**
 * Each {@link Annotation} we submit as part of a {@link Span} has an {@link EndPoint} defined. The {@link EndPoint} needs to
 * be set once for a service.
 * <p>
 * This is the interface that allows to initialize the endpoint for your service.
 * 
 * @author kristof
 */
public interface EndPointSubmitter {

    /**
     * Sets end point.
     * 
     * @param ip Ip address
     * @param port Port.
     * @param serviceName Service name.
     */
    void submit(final int ip, final int port, final String serviceName);

    /**
     * Gets end point.
     * 
     * @return {@link EndPoint}. Can be <code>null</code> in case not yet set.
     */
    EndPoint getEndPoint();

}
