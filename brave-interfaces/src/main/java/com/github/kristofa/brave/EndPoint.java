package com.github.kristofa.brave;

/**
 * TCP endpoint.
 * 
 * @author kristof
 */
public interface EndPoint {

    /**
     * Get Ip address.
     * 
     * @return Ip address.
     */
    int getIpAddress();

    /**
     * Get port.
     * 
     * @return Port.
     */
    int getPort();

    /**
     * Get service name.
     * 
     * @return Service name.
     */
    String getServiceName();
}
