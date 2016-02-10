package com.github.kristofa.brave.kafka;

import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.twitter.zipkin.gen.Span;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TBinaryProtocol;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processes spans by sending them, one at a time, to the topic `zipkin`, encoded in {@linkplain TBinaryProtocol}.
 * <p>
 * <p/> Note: this class was written to be used by a single-threaded executor, hence it is not thead-safe.
 */
class SpanProcessingTask implements Callable<Integer> {

    private static final Logger LOGGER = Logger.getLogger(SpanProcessingTask.class.getName());
    private final BlockingQueue<Span> queue;
    private final Producer<byte[], byte[]> producer;
    private final SpanCollectorMetricsHandler metricsHandler;
    private volatile boolean stop = false;
    private int numProcessedSpans = 0;


    SpanProcessingTask(BlockingQueue<Span> queue, Producer<byte[], byte[]> producer, SpanCollectorMetricsHandler metricsHandler) {
        this.queue = queue;
        this.producer = producer;
        this.metricsHandler = metricsHandler;
    }

    public void stop() {
        stop = true;
    }

    @Override
    public Integer call() throws Exception {
        final TSerializer serializer = new TSerializer(new TBinaryProtocol.Factory());
        do {
            final Span span = queue.poll(5, TimeUnit.SECONDS);
            if (span == null) {
                continue;
            }
            try {
                final ProducerRecord<byte[], byte[]> message = new ProducerRecord<>("zipkin", serializer.serialize(span));
                producer.send(message);
                numProcessedSpans++;
            } catch (TException e) {
                metricsHandler.incrementDroppedSpans(1);
                LOGGER.log(Level.WARNING, "TException when writing span.", e);
            }
        } while (!stop);
        return numProcessedSpans;
    }
}
