package com.github.kristofa.brave.zipkin;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;

import org.apache.commons.lang.Validate;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.Span;
import com.twitter.zipkin.gen.ZipkinCollector;

/**
 * Sends spans to Zipkin collector or Scribe.
 * <p/>
 * Typically the {@link ZipkinSpanCollector} should be a singleton in your application that can be used by both
 * {@link ClientTracer} as {@link ServerTracer}.
 * <p/>
 * This SpanCollector is implemented so it puts spans on a queue which are processed by a separate thread. In this way we are
 * submitting spans asynchronously and we should have minimal overhead on application performance.
 * <p/>
 * At this moment the number of processing threads is fixed and set to 1.
 * 
 * @author kristof
 */
public class ZipkinSpanCollector implements SpanCollector {

    private static final int DEFAULT_MAX_QUEUESIZE = 100;
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinSpanCollector.class);

    private final TTransport transport;
    private final ZipkinCollector.Client client;
    private final BlockingQueue<Span> spanQueue;
    private final ExecutorService executorService;
    private final SpanProcessingThread spanProcessingThread;
    private final Future<Integer> future;

    /**
     * Create a new instance with default queue size (=50).
     * 
     * @param zipkinCollectorHost Host for zipkin collector.
     * @param zipkinCollectorPort Port for zipkin collector.
     */
    public ZipkinSpanCollector(final String zipkinCollectorHost, final int zipkinCollectorPort) {
        this(zipkinCollectorHost, zipkinCollectorPort, DEFAULT_MAX_QUEUESIZE);
    }

    /**
     * Create a new instance.
     * 
     * @param zipkinCollectorHost Host for zipkin collector.
     * @param zipkinCollectorPort Port for zipkin collector.
     * @param maxQueueSize Maximum queue size.
     */
    public ZipkinSpanCollector(final String zipkinCollectorHost, final int zipkinCollectorPort, final int maxQueueSize) {
        Validate.notEmpty(zipkinCollectorHost);
        transport = new TFramedTransport(new TSocket(zipkinCollectorHost, zipkinCollectorPort));
        final TProtocol protocol = new TBinaryProtocol(transport);
        client = new ZipkinCollector.Client(protocol);
        try {
            transport.open();
        } catch (final TTransportException e) {
            throw new IllegalStateException(e);
        }
        spanQueue = new ArrayBlockingQueue<Span>(maxQueueSize);
        spanProcessingThread = new SpanProcessingThread(spanQueue, client);
        executorService = Executors.newSingleThreadExecutor();
        future = executorService.submit(spanProcessingThread);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collect(final Span span) {

        final long start = System.currentTimeMillis();
        try {
            final boolean offer = spanQueue.offer(span, 5, TimeUnit.SECONDS);
            if (!offer) {
                LOGGER.error("It took to long to offer Span to queue (more than 5 seconds). Span not submitted: " + span);
            } else {
                final long end = System.currentTimeMillis();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Adding span to queue took " + (end - start) + "ms.");
                }
            }
        } catch (final InterruptedException e1) {
            LOGGER.error("Unable to submit span to queue: " + span, e1);
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @PreDestroy
    public void close() {

        LOGGER.info("Stopping SpanProcessingThread.");
        spanProcessingThread.stop();
        try {
            final Integer spansProcessed = future.get();
            LOGGER.info("SpanProcessingThread processed " + spansProcessed + " spans.");
        } catch (final Exception e) {
            LOGGER.error("Exception when getting result of SpanProcessingThread.", e);
        }
        executorService.shutdown();
        transport.close();
        LOGGER.info("ZipkinSpanCollector closed.");
    }

}
