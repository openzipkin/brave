package com.github.kristofa.brave;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import org.apache.commons.lang3.Validate;

/**
 * {@link EndPoint} implementation.
 * 
 * @see EndPoint
 * @author kristof
 */
class EndPointImpl implements EndPoint {

    private final int ipv4;
    private final String ipAddress;
    private final int port;
    private final String serviceName;

    /**
     * Create a new instance.
     * 
     * @param ipv4 Ip address as integer.
     * @param port Port for service.
     * @param serviceName Service name. Should not be <code>null</code>.
     */
    EndPointImpl(final int ipv4, final int port, final String serviceName) {
        Validate.notEmpty(serviceName);
        this.ipv4 = ipv4;
        this.port = port;
        this.serviceName = serviceName;
        final byte[] ipByteArray = {(byte)(ipv4 >>> 24), (byte)(ipv4 >>> 16), (byte)(ipv4 >>> 8), (byte)ipv4};
        try {
            ipAddress = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }

    }

    /**
     * Create a new instance.
     * 
     * @param ipAddress Ip address as String (eg 127.0.0.1). Should not be <code>null</code>.
     * @param port Port for service.
     * @param serviceName Service name. Should not be <code>null</code>.
     */
    EndPointImpl(final String ipAddress, final int port, final String serviceName) {
        Validate.notEmpty(ipAddress);
        Validate.notEmpty(serviceName);
        this.ipAddress = ipAddress;
        this.port = port;
        this.serviceName = serviceName;
        InetAddress inetAddress = null;
        try {
            inetAddress = InetAddress.getByName(ipAddress);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
        ipv4 = ByteBuffer.wrap(inetAddress.getAddress()).getInt();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getIpv4() {
        return ipv4;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIpAddress() {
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
        result = prime * result + ipv4;
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
        if (ipv4 != other.ipv4) {
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
        return "[ipv4: " + ipv4 + ", ip: " + ipAddress + ", port: " + port + ", service name: " + serviceName + "]";
    }

}
