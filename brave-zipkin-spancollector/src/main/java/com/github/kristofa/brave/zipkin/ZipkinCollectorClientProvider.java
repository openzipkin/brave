package com.github.kristofa.brave.zipkin;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import com.twitter.zipkin.gen.ZipkinCollector;
import com.twitter.zipkin.gen.ZipkinCollector.Client;

import static com.github.kristofa.brave.internal.Util.checkNotBlank;

/**
 * {@link ThriftClientProvider} for ZipkinCollector.
 * 
 * @author adriaens
 */
class ZipkinCollectorClientProvider implements ThriftClientProvider<ZipkinCollector.Client> {

    private final static Logger LOGGER = Logger.getLogger(ZipkinCollectorClientProvider.class.getName());

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
        this.host = checkNotBlank(host, "Null or empty host");
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
            LOGGER.log(Level.FINE, "TransportException detected, closing current connection and opening new one", exception);
            // Close existing transport.
            close();

            try {
                setup();
            } catch (final TException e) {
                LOGGER.log(Level.WARNING, "Trying to reconnect to Thrift server failed.", e);
                return null;
            }
            return client;
        } else {
            LOGGER.log(Level.WARNING, "Thrift exception.", exception);
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
