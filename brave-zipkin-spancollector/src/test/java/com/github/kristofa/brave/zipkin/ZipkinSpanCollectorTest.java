package com.github.kristofa.brave.zipkin;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.NotImplementedException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.THsHaServer;
import org.apache.thrift.server.TServer;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TNonblockingServerSocket;
import org.apache.thrift.transport.TNonblockingServerTransport;
import org.apache.thrift.transport.TTransportException;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.zipkin.gen.LogEntry;
import com.twitter.zipkin.gen.ResultCode;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.StoreAggregatesException;
import com.twitter.zipkin.gen.ZipkinCollector;
import com.twitter.zipkin.gen.ZipkinCollector.Iface;
import com.twitter.zipkin.gen.ZipkinCollector.Processor;

public class ZipkinSpanCollectorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinSpanCollectorTest.class);

    private static final int PORT = 9410;
    private static final long SPAN_ID = 1;
    private static final long TRACE_ID = 2;
    private static final String SPAN_NAME = "SpanName";

    @Test(expected = IllegalArgumentException.class)
    public void testZipkinSpanCollector() {
        new ZipkinSpanCollector("", PORT);
    }

    @Test
    public void testCollect() throws TTransportException {
        final ZipkinCollectorReceiver zipkinCollectorReceiver = new ZipkinCollectorReceiver();
        final Processor<Iface> processor = new ZipkinCollector.Processor<Iface>(zipkinCollectorReceiver);

        final TNonblockingServerTransport transport = new TNonblockingServerSocket(PORT);
        final THsHaServer.Args args = new THsHaServer.Args(transport);

        args.workerThreads(1);
        args.processor(processor);
        args.protocolFactory(new TBinaryProtocol.Factory());
        args.transportFactory(new TFramedTransport.Factory());

        final TServer server = new THsHaServer(args);

        final Runnable serveThread = new Runnable() {

            @Override
            public void run() {
                server.serve();
            }
        };

        new Thread(serveThread).start();
        try {

            final ZipkinSpanCollector zipkinSpanCollector = new ZipkinSpanCollector("localhost", PORT);
            try {
                final Span span = new Span();
                span.setId(SPAN_ID);
                span.setTrace_id(TRACE_ID);
                span.setName(SPAN_NAME);
                zipkinSpanCollector.collect(span);

            } finally {
                zipkinSpanCollector.close();
            }
            final Span serverCollectedSpan = zipkinCollectorReceiver.getSpan();
            assertEquals(SPAN_ID, serverCollectedSpan.getId());
            assertEquals(TRACE_ID, serverCollectedSpan.getTrace_id());
            assertEquals(SPAN_NAME, serverCollectedSpan.getName());

        } finally {
            server.stop();
        }

    }

    public class ZipkinCollectorReceiver implements Iface {

        private final Span span = new Span();

        @Override
        public ResultCode Log(final List<LogEntry> messages) throws TException {
            try {
                assertEquals("Expect only 1 LogEntry.", 1, messages.size());
                final LogEntry logEntry = messages.get(0);

                final Base64 base64 = new Base64();
                final byte[] decodedSpan = base64.decode(logEntry.getMessage());

                final ByteArrayInputStream buf = new ByteArrayInputStream(decodedSpan);
                final TProtocolFactory factory = new TBinaryProtocol.Factory();
                final TProtocol proto = factory.getProtocol(new TIOStreamTransport(buf));
                span.read(proto);

            } catch (final TException e) {
                LOGGER.error("TException when getting result.", e);
                return ResultCode.TRY_LATER;
            }
            return ResultCode.OK;
        }

        @Override
        public void storeTopAnnotations(final String service_name, final List<String> annotations)
            throws StoreAggregatesException, TException {
            throw new NotImplementedException();

        }

        @Override
        public void storeTopKeyValueAnnotations(final String service_name, final List<String> annotations)
            throws StoreAggregatesException, TException {
            throw new NotImplementedException();

        }

        @Override
        public void storeDependencies(final String service_name, final List<String> endpoints)
            throws StoreAggregatesException, TException {
            throw new NotImplementedException();

        }

        public Span getSpan() {
            return span;
        }

    }
}
