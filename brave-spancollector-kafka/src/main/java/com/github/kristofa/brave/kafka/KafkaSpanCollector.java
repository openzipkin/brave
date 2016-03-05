package com.github.kristofa.brave.kafka;

import com.github.kristofa.brave.SpanCollector;

import java.io.Closeable;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.twitter.zipkin.gen.Span;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;

/**
 * SpanCollector which submits spans to Kafka using <a href="http://kafka.apache.org/documentation.html#producerapi">Kafka Producer api</a>.
 * <p>
 * Spans are sent to kafka as keyed messages: the key is the topic zipkin and the value is a TBinaryProtocol encoded Span.
 * </p>
 */
public class KafkaSpanCollector implements SpanCollector, Closeable {

    private static final Logger LOGGER = Logger.getLogger(KafkaSpanCollector.class.getName());
    private static final Properties DEFAULT_PROPERTIES = new Properties();

    static {
        DEFAULT_PROPERTIES.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        DEFAULT_PROPERTIES.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    }

    private static Properties defaultPropertiesWith(String bootstrapServers) {
        Properties props = new Properties();
        for (String name : DEFAULT_PROPERTIES.stringPropertyNames()) {
            props.setProperty(name, DEFAULT_PROPERTIES.getProperty(name));
        }
        props.setProperty("bootstrap.servers", bootstrapServers);
        return props;
    }

    private final Producer<byte[], byte[]> producer;
    private final ExecutorService executorService;
    private final SpanProcessingTask spanProcessingTask;
    private final Future<Integer> future;
    private final BlockingQueue<Span> queue;
    private final SpanCollectorMetricsHandler metricsHandler;

    /**
     * Create a new instance with default configuration.
     *
     * @param bootstrapServers A list of host/port pairs to use for establishing the initial connection to the Kafka cluster.
     *                         Like: host1:port1,host2:port2,... Does not to be all the servers part of Kafka cluster.
     * @param metricsHandler   Gets notified when spans are accepted or dropped. If you are not interested in these events you
     *                         can use {@linkplain EmptySpanCollectorMetricsHandler}
     */
    public KafkaSpanCollector(String bootstrapServers, SpanCollectorMetricsHandler metricsHandler) {
        this(KafkaSpanCollector.defaultPropertiesWith(bootstrapServers), metricsHandler);
    }

    /**
     * KafkaSpanCollector.
     *
     * @param kafkaProperties Configuration for Kafka producer. Essential configuration properties are:
     *                        bootstrap.servers, key.serializer, value.serializer. For a
     *                        full list of config options, see http://kafka.apache.org/documentation.html#producerconfigs.
     * @param metricsHandler  Gets notified when spans are accepted or dropped. If you are not interested in these events you
     *                        can use {@linkplain EmptySpanCollectorMetricsHandler}
     */
    public KafkaSpanCollector(Properties kafkaProperties, SpanCollectorMetricsHandler metricsHandler) {
        producer = new KafkaProducer<>(kafkaProperties);
        this.metricsHandler = metricsHandler;
        executorService = Executors.newSingleThreadExecutor();
        queue = new ArrayBlockingQueue<Span>(1000);
        spanProcessingTask = new SpanProcessingTask(queue, producer, metricsHandler);
        future = executorService.submit(spanProcessingTask);
    }

    @Override
    public void collect(com.twitter.zipkin.gen.Span span) {
        metricsHandler.incrementAcceptedSpans(1);
        if (!queue.offer(span)) {
            metricsHandler.incrementDroppedSpans(1);
            LOGGER.log(Level.WARNING, "Queue rejected span!");
        }
    }

    @Override
    public void addDefaultAnnotation(String key, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        spanProcessingTask.stop();
        try {
            Integer nrProcessedSpans = future.get(6000, TimeUnit.MILLISECONDS);
            LOGGER.info("SpanProcessingTask processed " + nrProcessedSpans + " spans.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception when waiting for SpanProcessTask to finish.", e);
        }
        executorService.shutdown();
        producer.close();
        metricsHandler.incrementDroppedSpans(queue.size());
        LOGGER.info("KafkaSpanCollector closed.");
    }
}