package com.github.kristofa.brave.zipkin;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.twitter.zipkin.gen.LogEntry;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.ZipkinCollector;

/**
 * Thread implementation that is responsible for submitting spans to the Zipkin span collector or Scribe. The thread takes
 * spans from a queue. The spans are produced by {@link ZipkinSpanCollector}, put on a queue and consumed and processed by
 * this thread.
 * 
 * @author adriaens
 */
class SpanProcessingThread implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpanProcessingThread.class);

    private final BlockingQueue<Span> queue;
    private final ZipkinCollector.Client client;
    private final Base64 base64 = new Base64();
    private final TProtocolFactory protocolFactory;
    private boolean stop = false;
    private int processedSpans = 0;

    /**
     * Creates a new instance.
     * 
     * @param queue BlockingQueue that will provide spans.
     * @param client Client used to submit spans to zipkin span collector or Scribe.
     */
    public SpanProcessingThread(final BlockingQueue<Span> queue, final ZipkinCollector.Client client) {
        this.queue = queue;
        this.client = client;
        protocolFactory = new TBinaryProtocol.Factory();
    }

    /**
     * Requests the thread to stop.
     */
    public void stop() {
        stop = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer call() throws Exception {

        do {
            final Span span = queue.poll(5000, TimeUnit.SECONDS);
            if (span != null) {

                final long start = System.currentTimeMillis();

                try {
                    final String spanAsString = base64.encodeToString(spanToBytes(span));
                    final LogEntry logEntry = new LogEntry("zipkin", spanAsString);
                    client.Log(Arrays.asList(logEntry));
                } catch (final TException e) {
                    LOGGER.error("Exception when trying to log Span.", e);
                } finally {
                    if (LOGGER.isDebugEnabled()) {
                        final long end = System.currentTimeMillis();
                        LOGGER.debug("Submitting span to service took " + (end - start) + "ms.");
                    }
                }
                processedSpans++;
            }
        } while (stop == false);
        return processedSpans;
    }

    private byte[] spanToBytes(final Span thriftSpan) throws TException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final TProtocol proto = protocolFactory.getProtocol(new TIOStreamTransport(buf));
        thriftSpan.write(proto);
        return buf.toByteArray();
    }

}
