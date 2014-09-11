package com.github.kristofa.brave.zipkin;

import org.apache.commons.lang.Validate;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.zipkin.gen.ZipkinCollector;
import com.twitter.zipkin.gen.ZipkinCollector.Client;

/**
 * {@link ThriftClientProvider} for ZipkinCollector.
 * 
 * @author adriaens
 */
class ZipkinCollectorClientProvider implements ThriftClientProvider<ZipkinCollector.Client> {

    private final static Logger LOGGER = LoggerFactory.getLogger(ZipkinCollectorClientProvider.class);

    private final String host;
    private final int port;
    private final int timeout;
    private TTransport transport;
    private ZipkinCollector.Client client;

    /**
     * Create a new instance.
     * 
     * @param host Host. Should not be empty.
     * @param port Port.
     * @param timeout Socket time out in milliseconds.
     */
    public ZipkinCollectorClientProvider(final String host, final int port, final int timeout) {
        Validate.notEmpty(host);
        this.host = host;
        this.port = port;
        this.timeout = timeout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setup() throws TException {
        final TSocket socket = new TSocket(host, port);
        socket.setTimeout(timeout);
        transport = new TFramedTransport(socket);
        final TProtocol protocol = new TBinaryProtocol(transport);
        client = new ZipkinCollector.Client(protocol);
        transport.open();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Client getClient() {
        return client;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Client exception(final TException exception) {
        if (exception instanceof TTransportException) {
            LOGGER.debug("TransportException detected, closing current connection and opening new one", exception);
            // Close existing transport.
            close();

            try {
                setup();
            } catch (final TException e) {
                LOGGER.warn("Trying to reconnect to Thrift server failed.", e);
                return null;
            }
            return client;
        } else {
            LOGGER.warn("Thrift exception.", exception);
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        if (transport != null) {
            transport.close();
        }
    }

}
