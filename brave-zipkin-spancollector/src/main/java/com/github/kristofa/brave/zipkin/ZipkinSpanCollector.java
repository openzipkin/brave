package com.github.kristofa.brave.zipkin;

import java.io.Closeable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.ServerTracer;
import com.github.kristofa.brave.SpanCollector;

import org.apache.thrift.TException;

import com.twitter.zipkin.gen.AnnotationType;
import com.twitter.zipkin.gen.BinaryAnnotation;
import com.twitter.zipkin.gen.Span;

import static com.github.kristofa.brave.internal.Util.checkNotBlank;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

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
public class ZipkinSpanCollector implements SpanCollector, Closeable {

    private static final String UTF_8 = "UTF-8";
    private static final Logger LOGGER = Logger.getLogger(ZipkinSpanCollector.class.getName());

    private final BlockingQueue<Span> spanQueue;
    private final ExecutorService executorService;
    private final List<SpanProcessingThread> spanProcessingThreads = new ArrayList<SpanProcessingThread>();
    private final List<ZipkinCollectorClientProvider> clientProviders = new ArrayList<>();
    private final List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
    private final Set<BinaryAnnotation> defaultAnnotations = new HashSet<BinaryAnnotation>();

    /**
     * Create a new instance with default queue size (= {@link ZipkinSpanCollectorParams#DEFAULT_QUEUE_SIZE}) and default
     * batch size (= {@link ZipkinSpanCollectorParams#DEFAULT_BATCH_SIZE}).
     *
     * @param zipkinCollectorHost Host for zipkin collector.
     * @param zipkinCollectorPort Port for zipkin collector.
     */
    public ZipkinSpanCollector(final String zipkinCollectorHost, final int zipkinCollectorPort) {
        this(zipkinCollectorHost, zipkinCollectorPort, new ZipkinSpanCollectorParams());
    }

    /**
     * Create a new instance.
     *
     * @param zipkinCollectorHost Host for zipkin collector.
     * @param zipkinCollectorPort Port for zipkin collector.
     * @param params Zipkin Span Collector parameters.
     */
    public ZipkinSpanCollector(final String zipkinCollectorHost, final int zipkinCollectorPort,
            final ZipkinSpanCollectorParams params) {
        checkNotBlank(zipkinCollectorHost, "Null or empty zipkinCollectorHost");
        checkNotNull(params, "Null params");

        spanQueue = new ArrayBlockingQueue<Span>(params.getQueueSize());
        executorService = Executors.newFixedThreadPool(params.getNrOfThreads());

        for (int i = 1; i <= params.getNrOfThreads(); i++) {

            // Creating a client provider for every spanProcessingThread.
            ZipkinCollectorClientProvider clientProvider = createZipkinCollectorClientProvider(zipkinCollectorHost,
                    zipkinCollectorPort, params);
            final SpanProcessingThread spanProcessingThread = new SpanProcessingThread(spanQueue, clientProvider,
                    params.getBatchSize());
            spanProcessingThreads.add(spanProcessingThread);
            clientProviders.add(clientProvider);
            futures.add(executorService.submit(spanProcessingThread));
        }
    }

    private ZipkinCollectorClientProvider createZipkinCollectorClientProvider(String zipkinCollectorHost,
            int zipkinCollectorPort, ZipkinSpanCollectorParams params) {
        ZipkinCollectorClientProvider clientProvider = new ZipkinCollectorClientProvider(zipkinCollectorHost,
                zipkinCollectorPort, params.getSocketTimeout());
        try {
            clientProvider.setup();
        } catch (final TException e) {
            if (params.failOnSetup()) {
                throw new IllegalStateException(e);
            } else {
                LOGGER.log(Level.WARNING, "Connection could not be established during setup.", e);
            }
        }
        return clientProvider;
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
            LOGGER.warning("Queue rejected Span, span not submitted: "+ span);
        } else {
            final long end = System.currentTimeMillis();
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Adding span to queue took " + (end - start) + "ms.");
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDefaultAnnotation(final String key, final String value) {
        checkNotBlank(key, "Null or blank key");
        checkNotNull(value, "Null value");

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
    public void close() {

        LOGGER.info("Stopping SpanProcessingThread.");
        for (final SpanProcessingThread thread : spanProcessingThreads) {
            thread.stop();
        }
        for (final Future<Integer> future : futures) {
            try {
                final Integer spansProcessed = future.get();
                LOGGER.info("SpanProcessingThread processed " + spansProcessed + "spans.");
            } catch (final Exception e) {
                LOGGER.log(Level.WARNING, "Exception when getting result of SpanProcessingThread.", e);
            }
        }
        for(final ZipkinCollectorClientProvider clientProvider : clientProviders) {
            clientProvider.close();
        }
        executorService.shutdown();
        LOGGER.info("ZipkinSpanCollector closed.");
    }

}
