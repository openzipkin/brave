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
     * @param ip Ip address, example 10.0.1.5
     * @param port Port.
     * @param serviceName Service name.
     */
    void submit(final String ip, final int port, final String serviceName);

    /**
     * Indicates if EndPoint has already been set.
     * 
     * @return <code>true</code> in case end point has already been submitted, <code>false</code> in case it has not been
     *         submitted yet.
     */
    boolean endPointSubmitted();

}
