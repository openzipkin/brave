package com.github.kristofa.brave.zipkin;

import org.apache.commons.lang3.Validate;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

/**
 * Default implementation: uses the socket transport from Thrift.
 *
 * @author botizac
 */
public final class SocketTransportProvider implements ZipkinClientTransportProvider {

    /**
     * Zipkin server collector host.
     */
    private final String host;

    /**
     * Zipkin server collector port.
     */
    private final int port;

    /**
     * Optional: socket connect timeout.
     */
    private int socketTimeout = 0;

    public SocketTransportProvider(String host, int port) {
        Validate.notEmpty(host);
        this.host = host;
        this.port = port;
    }

    /**
     * Constructor with all required arguments.
     *
     * @param host          Zipkin server host/IP
     * @param port          Zipkin server port
     * @param socketTimeout Socket timeout
     */
    public SocketTransportProvider(String host, int port, int socketTimeout) {
        this(host, port);
        this.socketTimeout = socketTimeout;
    }

    @Override
    public TTransport getTransport() {
        if (socketTimeout > 0) {
            return new TSocket(host, port, socketTimeout);
        }

        return new TSocket(host, port);
    }
}
