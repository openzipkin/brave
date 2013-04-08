package com.github.kristofa.brave;

/**
 * TCP endpoint.
 * 
 * @author kristof
 */
public interface EndPoint {

    /**
     * Get Ip address as integer.
     * 
     * @return Ip address as integer.
     */
    int getIpv4();

    /**
     * Gets the ip address as String.
     * 
     * @return Ip address as String. Example 127.0.0.1
     */
    String getIpAddress();

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
