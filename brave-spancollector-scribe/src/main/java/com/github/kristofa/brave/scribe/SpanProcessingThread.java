package com.github.kristofa.brave.scribe;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;

import com.twitter.zipkin.gen.LogEntry;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.scribe.Client;

import static com.github.kristofa.brave.internal.Util.checkNotNull;
import static java.lang.String.format;

/**
 * Thread implementation that is responsible for submitting spans to a Scribe compatible destination. The thread takes
 * spans from a queue. The spans are produced by {@link ScribeSpanCollector} put on a queue and consumed and processed by
 * this thread.
 * <p/>
 * We will try to buffer spans and send them in batches to minimize communication overhead. However if the batch size is not
 * reached within 2 polls (max 10 seconds) the available spans will be sent over anyway.
 * 
 * @see ScribeSpanCollector
 * @author kristof
 */
class SpanProcessingThread implements Callable<Integer> {

    private static final Logger LOGGER = Logger.getLogger(SpanProcessingThread.class.getName());
    private static final int MAX_SUBSEQUENT_EMPTY_BATCHES = 2;

    private final BlockingQueue<Span> queue;
    private final ScribeClientProvider clientProvider;
    private final TProtocolFactory protocolFactory;
    private final ScribeCollectorMetricsHandler metricsHandler;
    private volatile boolean stop = false;
    private int processedSpans = 0;
    private final List<LogEntry> logEntries;
    private final int maxBatchSize;

    /**
     * Creates a new instance.
     * 
     * @param queue BlockingQueue that will provide spans.
     * @param clientProvider {@link ThriftClientProvider} that provides client used to submit spans to zipkin span collector.
     * @param maxBatchSize Max batch size. Indicates how many spans we submit to collector in 1 go.
     * @param metricsHandler Handler to be notified of span logging events.
     */
    public SpanProcessingThread(final BlockingQueue<Span> queue, final ScribeClientProvider clientProvider,
        final int maxBatchSize, ScribeCollectorMetricsHandler metricsHandler) {
        if (maxBatchSize <= 0) throw new IllegalArgumentException("maxBatchSize must be positive");
        this.queue = checkNotNull(queue, "Null queue");
        this.clientProvider = checkNotNull(clientProvider, "Null clientProvider");
        this.metricsHandler = checkNotNull(metricsHandler, "Null metricsHandler");
        protocolFactory = new TBinaryProtocol.Factory();
        this.maxBatchSize = maxBatchSize;
        logEntries = new ArrayList<LogEntry>(maxBatchSize);
    }

    /**
     * Requests the thread to stop as well as closes the client connection for this thread.
     */
    public void stop() {
        stop = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer call() {

        int subsequentEmptyBatches = 0;
        do {

            try {
                final Span span = queue.poll(5, TimeUnit.SECONDS);
                if (span == null) {
                    subsequentEmptyBatches++;

                } else {
                    logEntries.add(create(span));
                }

                if (subsequentEmptyBatches >= MAX_SUBSEQUENT_EMPTY_BATCHES && !logEntries.isEmpty()
                    || logEntries.size() >= maxBatchSize || !logEntries.isEmpty() && stop) {
                    log(logEntries);
                    logEntries.clear();
                    subsequentEmptyBatches = 0;
                }
            } catch (final Exception e) {
                LOGGER.log(Level.WARNING, "Unexpected exception flushing spans", e);
            }

        } while (stop == false);
        return processedSpans;
    }

    private void log(final List<LogEntry> logEntries) {
        final long start = System.currentTimeMillis();
        final boolean success = log(clientProvider.getClient(), logEntries);
        processedSpans += logEntries.size();
        if (success && LOGGER.isLoggable(Level.FINE)) {
            final long end = System.currentTimeMillis();
            LOGGER.fine("Submitting " + logEntries.size() + " spans to service took " + (end - start) + "ms.");
        }
    }

    private boolean log(final Client client, final List<LogEntry> logEntries) {
        try {
            clientProvider.getClient().Log(logEntries);
            return true;
        } catch (final TException e) {
            LOGGER.fine(format("Exception when trying to log Span.  Will retry: %s", e.getMessage()));
            final Client newClient = clientProvider.exception(e);
            if (newClient != null) {
                LOGGER.fine("Got new client with new connection. Logging with new client.");
                try {
                    newClient.Log(logEntries);
                    return true;
                } catch (final TException e2) {
                    LOGGER.log(Level.WARNING, "Logging spans failed. " + logEntries.size() + " spans are lost!", e2);
                }
            } else {
                LOGGER.warning("Logging spans failed (couldn't establish connection). " + logEntries.size() + " spans are lost!");
            }
        }
        metricsHandler.incrementDroppedSpans(logEntries.size());
        return false;
    }

    private LogEntry create(final Span span) throws TException {
        final String spanAsString = Base64.encode(spanToBytes(span));
        return new LogEntry("zipkin", spanAsString);
    }

    private byte[] spanToBytes(final Span thriftSpan) throws TException {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final TProtocol proto = protocolFactory.getProtocol(new TIOStreamTransport(buf));
        thriftSpan.write(proto);
        return buf.toByteArray();
    }

}
