package com.github.kristofa.brave.zipkin;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.Validate;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.LogEntry;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.ZipkinCollector;

/**
 * Sends spans to Zipkin collector.
 * <p>
 * Typically the {@link ZipkinSpanCollector} should be a singleton in your application that can be used by both
 * {@link ClientTracer} as {@link ServerTracer}.
 * 
 * @author kristof
 */
public class ZipkinSpanCollector implements SpanCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinSpanCollector.class);

    private final TProtocolFactory protocolFactory;
    private final TTransport transport;
    private final Base64 base64 = new Base64();
    private final ZipkinCollector.Client client;

    /**
     * Create a new instance.
     * 
     * @param zipkinCollectorHost Host for zipkin collector.
     * @param zipkinCollectorPort Port for zipkin collector.
     */
    public ZipkinSpanCollector(final String zipkinCollectorHost, final int zipkinCollectorPort) {
        Validate.notEmpty(zipkinCollectorHost);
        protocolFactory = new TBinaryProtocol.Factory();
        transport = new TFramedTransport(new TSocket(zipkinCollectorHost, zipkinCollectorPort));
        final TProtocol protocol = new TBinaryProtocol(transport);
        client = new ZipkinCollector.Client(protocol);
        try {
            transport.open();
        } catch (final TTransportException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collect(final Span span) {

        final long start = System.currentTimeMillis();

        try {
            final String spanAsString = base64.encodeToString(spanToBytes(span));
            final LogEntry logEntry = new LogEntry("zipkin", spanAsString);
            client.Log(Arrays.asList(logEntry));
        } catch (final TException e) {
            throw new IllegalStateException(e);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                final long end = System.currentTimeMillis();
                LOGGER.debug("Persisting span took " + (end - start) + "ms.");
            }
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        transport.close();
    }

    private byte[] spanToBytes(final Span thriftSpan) throws TException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final TProtocol proto = protocolFactory.getProtocol(new TIOStreamTransport(buf));
        thriftSpan.write(proto);
        return buf.toByteArray();
    }
}
