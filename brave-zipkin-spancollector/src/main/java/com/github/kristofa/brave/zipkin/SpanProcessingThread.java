package com.github.kristofa.brave.zipkin;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
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
 * <p/>
 * We will try to buffer spans and send them in batches to minimize communication overhead. However if the batch size is not
 * reached within 2 polls (max 10 seconds) the available spans will be sent over anyway.
 * 
 * @author adriaens
 */
class SpanProcessingThread implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpanProcessingThread.class);
    private static final int MAX_BATCH_SIZE = 50;
    private static final int MAX_SUBSEQUENT_EMPTY_BATCHES = 2;

    private final BlockingQueue<Span> queue;
    private final ZipkinCollector.Client client;
    private final Base64 base64 = new Base64();
    private final TProtocolFactory protocolFactory;
    private boolean stop = false;
    private int processedSpans = 0;
    private final List<LogEntry> logEntries;

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
        logEntries = new ArrayList<LogEntry>(MAX_BATCH_SIZE);
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

        int subsequentEmptyBatches = 0;
        do {

            final Span span = queue.poll(5, TimeUnit.SECONDS);
            if (span == null) {
                subsequentEmptyBatches++;

            } else {
                logEntries.add(create(span));
            }

            if (subsequentEmptyBatches >= MAX_SUBSEQUENT_EMPTY_BATCHES && !logEntries.isEmpty()
                || logEntries.size() >= MAX_BATCH_SIZE || !logEntries.isEmpty() && stop) {
                log(logEntries);
                logEntries.clear();
                subsequentEmptyBatches = 0;
            }

        } while (stop == false);
        return processedSpans;
    }

    private void log(final List<LogEntry> logEntries) {
        final long start = System.currentTimeMillis();
        try {
            client.Log(logEntries);
        } catch (final TException e) {
            LOGGER.error("Exception when trying to log Span.", e);
        } finally {
            if (LOGGER.isDebugEnabled()) {
                final long end = System.currentTimeMillis();
                LOGGER.debug("Submitting " + logEntries.size() + " spans to service took " + (end - start) + "ms.");
            }
        }
        processedSpans += logEntries.size();
    }

    private LogEntry create(final Span span) throws TException {
        final String spanAsString = base64.encodeToString(spanToBytes(span));
        return new LogEntry("zipkin", spanAsString);
    }

    private byte[] spanToBytes(final Span thriftSpan) throws TException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final TProtocol proto = protocolFactory.getProtocol(new TIOStreamTransport(buf));
        thriftSpan.write(proto);
        return buf.toByteArray();
    }

}
