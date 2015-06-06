package com.github.kristofa.brave.zipkin;

import com.twitter.zipkin.gen.ZipkinCollector;
import com.twitter.zipkin.gen.ZipkinCollector.Client;
import org.apache.commons.lang.Validate;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ThriftClientProvider} for ZipkinCollector.
 * <p>
 * Implementation note: Always uses the {@link org.apache.thrift.transport.TFramedTransport framed transport}.
 * The underlying transport for that, however, is meant to be configurable. Therefore, the constructor requires
 * a {@link com.github.kristofa.brave.zipkin.ZipkinClientTransportProvider client transport provider}, which is used
 * to create the underlying transport implementation.
 * </p>
 *
 * @author adriaens
 * @author cbotiza
 */
class ZipkinCollectorClientProvider implements ThriftClientProvider<ZipkinCollector.Client> {

    private final static Logger LOGGER = LoggerFactory.getLogger(ZipkinCollectorClientProvider.class);

    private TTransport transport;
    private ZipkinCollector.Client client;

    /**
     * Provider of underlying Thrift transport.
     */
    private ZipkinClientTransportProvider transportProvider;

    public ZipkinCollectorClientProvider(final ZipkinClientTransportProvider transportProvider) {
        Validate.notNull(transportProvider);
        this.transportProvider = transportProvider;
    }

    /**
     * Convenience constructor. Uses the socket implementation as underlying transport.
     *
     * @param host    Host. Should not be empty.
     * @param port    Port.
     * @param timeout Socket time out in milliseconds.
     * @deprecated Use the transport based constructor.
     */
    @Deprecated
    public ZipkinCollectorClientProvider(final String host, final int port, final int timeout) {
        this(new SocketTransportProvider(host, port, timeout));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setup() throws TException {
        final TTransport underlying = transportProvider.getTransport();
        transport = new TFramedTransport(underlying);
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
