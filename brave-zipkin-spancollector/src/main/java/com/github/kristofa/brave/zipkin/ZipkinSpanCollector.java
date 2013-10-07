package com.github.kristofa.brave.zipkin;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PreDestroy;

import org.apache.commons.lang.Validate;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

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

    private static final String UTF_8 = "UTF-8";
    private static final int DEFAULT_MAX_QUEUESIZE = 300;
    private static final Logger LOGGER = LoggerFactory.getLogger(ZipkinSpanCollector.class);

    private final ZipkinCollectorClientProvider clientProvider;
    private final BlockingQueue<Span> spanQueue;
    private final ExecutorService executorService;
    private final SpanProcessingThread spanProcessingThread;
    private final Future<Integer> future;
    private final Set<BinaryAnnotation> defaultAnnotations = new HashSet<BinaryAnnotation>();

    /**
     * Create a new instance with default queue size (=300) and default batch size (=10).
     * 
     * @param zipkinCollectorHost Host for zipkin collector.
     * @param zipkinCollectorPort Port for zipkin collector.
     */
    public ZipkinSpanCollector(final String zipkinCollectorHost, final int zipkinCollectorPort) {
        this(zipkinCollectorHost, zipkinCollectorPort, DEFAULT_MAX_QUEUESIZE, SpanProcessingThread.DEFAULT_MAX_BATCH_SIZE);
    }

    /**
     * Create a new instance.
     * 
     * @param zipkinCollectorHost Host for zipkin collector.
     * @param zipkinCollectorPort Port for zipkin collector.
     * @param maxQueueSize Maximum queue size.
     * @param maxBatchSize Maximum number of spans that is sent to collector in 1 go.
     */
    public ZipkinSpanCollector(final String zipkinCollectorHost, final int zipkinCollectorPort, final int maxQueueSize,
        final int maxBatchSize) {
        Validate.notEmpty(zipkinCollectorHost);
        clientProvider = new ZipkinCollectorClientProvider(zipkinCollectorHost, zipkinCollectorPort);
        try {
            clientProvider.setup();
        } catch (final TException e) {
            throw new IllegalStateException(e);
        }
        spanQueue = new ArrayBlockingQueue<Span>(maxQueueSize);
        spanProcessingThread = new SpanProcessingThread(spanQueue, clientProvider, maxBatchSize);
        executorService = Executors.newSingleThreadExecutor();
        future = executorService.submit(spanProcessingThread);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void collect(final Span span) {

        final long start = System.currentTimeMillis();

        if (!defaultAnnotations.isEmpty()) {
            for (final BinaryAnnotation ba : defaultAnnotations) {
                span.addToBinary_annotations(ba);
            }
        }

        final boolean offer = spanQueue.offer(span);
        if (!offer) {
            LOGGER.error("Queue rejected Span, span not submitted: " + span);
        } else {
            final long end = System.currentTimeMillis();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Adding span to queue took " + (end - start) + "ms.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDefaultAnnotation(final String key, final String value) {
        Validate.notEmpty(key);
        Validate.notNull(value);

        try {
            final ByteBuffer bb = ByteBuffer.wrap(value.getBytes(UTF_8));

            final BinaryAnnotation binaryAnnotation = new BinaryAnnotation();
            binaryAnnotation.setKey(key);
            binaryAnnotation.setValue(bb);
            binaryAnnotation.setAnnotation_type(AnnotationType.STRING);
            defaultAnnotations.add(binaryAnnotation);

        } catch (final UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
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
        clientProvider.close();
        LOGGER.info("ZipkinSpanCollector closed.");
    }

}
