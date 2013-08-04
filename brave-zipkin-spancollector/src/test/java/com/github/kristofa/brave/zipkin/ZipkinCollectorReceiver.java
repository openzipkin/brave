package com.github.kristofa.brave.zipkin;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.NotImplementedException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.zipkin.gen.LogEntry;
import com.twitter.zipkin.gen.ResultCode;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.StoreAggregatesException;
import com.twitter.zipkin.gen.ZipkinCollector.Iface;

class ZipkinCollectorReceiver implements Iface {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinCollectorReceiver.class);
    private final List<Span> spans = new ArrayList<Span>();
    private final int delayMs;

    public ZipkinCollectorReceiver(final int delayMs) {
        this.delayMs = delayMs;
    }

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
            final Span span = new Span();
            span.read(proto);
            spans.add(span);

            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (final InterruptedException e) {
                    LOGGER.error("Interrupted.", e);
                }
            }

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
    public void storeDependencies(final String service_name, final List<String> endpoints) throws StoreAggregatesException,
        TException {
        throw new NotImplementedException();

    }

    /**
     * Gets the received spans in the order as they were received.
     * 
     * @return Received spans.
     */
    public List<Span> getSpans() {
        return new ArrayList<Span>(spans);
    }

}
