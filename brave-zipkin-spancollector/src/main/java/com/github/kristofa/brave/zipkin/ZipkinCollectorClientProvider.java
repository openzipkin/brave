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
    private TTransport transport;
    private ZipkinCollector.Client client;

    /**
     * Create a new instance.
     * 
     * @param host Host. Should not be empty.
     * @param port Port.
     */
    public ZipkinCollectorClientProvider(final String host, final int port) {
        Validate.notEmpty(host);
        this.host = host;
        this.port = port;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setup() throws TException {
        transport = new TFramedTransport(new TSocket(host, port));
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
        LOGGER.error("Thrift exception.", exception);
        if (exception instanceof TTransportException) {
            // Close existing transport.
            close();

            try {
                setup();
            } catch (final TException e) {
                LOGGER.error("Trying to reconnect to Thrift server failed.", e);
                return null;
            }
            return client;
        }
        return null;
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
