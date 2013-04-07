package com.github.kristofa.brave;

import org.apache.commons.lang3.Validate;

/**
 * {@link EndPoint} implementation.
 * 
 * @see EndPoint
 * @author kristof
 */
class EndPointImpl implements EndPoint {

    private final int ipAddress;
    private final int port;
    private final String serviceName;

    /**
     * Creates a new instance.
     * 
     * @param ipAddress Ip address.
     * @param port Port.
     * @param serviceName Service name, should not be <code>null</code> or empty.
     */
    EndPointImpl(final int ipAddress, final int port, final String serviceName) {
        Validate.notEmpty(serviceName);
        this.ipAddress = ipAddress;
        this.port = port;
        this.serviceName = serviceName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIpAddress() {
        return ipAddress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPort() {
        return port;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getServiceName() {
        return serviceName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ipAddress;
        result = prime * result + port;
        result = prime * result + serviceName.hashCode();
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final EndPointImpl other = (EndPointImpl)obj;
        if (ipAddress != other.ipAddress) {
            return false;
        }
        if (port != other.port) {
            return false;
        }
        if (!serviceName.equals(other.serviceName)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "[ip: " + ipAddress + ", port: " + port + ", service name: " + serviceName + "]";
    }

}
