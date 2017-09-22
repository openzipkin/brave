package com.github.kristofa.brave.kafka;

import com.github.kristofa.brave.AbstractSpanCollector;
import com.github.kristofa.brave.EmptySpanCollectorMetricsHandler;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.google.auto.value.AutoValue;
import com.twitter.zipkin.gen.SpanCodec;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * SpanCollector which sends a thrift-encoded list of spans to a Kafka topic (default: "zipkin")
 *
 * <p><b>Important</b> If using zipkin-collector-service (or zipkin-receiver-kafka), you must run v1.35+
 *
 * @deprecated replaced by {@link zipkin2.reporter.AsyncReporter} and {@code KafkaSender}
 *             located in the "io.zipkin.reporter2:zipkin-sender-kafka11" dependency.
 */
@Deprecated
public final class KafkaSpanCollector extends AbstractSpanCollector {

  @AutoValue
  public static abstract class Config {
    public static Builder builder() {
      return new AutoValue_KafkaSpanCollector_Config.Builder()
          .topic("zipkin")
          .flushInterval(1);
    }

    public static Builder builder(String bootstrapServers) {
      Properties props = new Properties();
      props.put("bootstrap.servers", bootstrapServers);
      props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
      props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
      return builder().kafkaProperties(props);
    }

    abstract Properties kafkaProperties();

    abstract int flushInterval();

    abstract String topic();

    @AutoValue.Builder
    public interface Builder {
      /**
       * Configuration for Kafka producer. Essential configuration properties are:
       * bootstrap.servers, key.serializer, value.serializer. For a full list of config options, see
       * http://kafka.apache.org/documentation.html#kafkaPropertiess.
       *
       * <p>Must include the following mappings:
       */
      Builder kafkaProperties(Properties kafkaProperties);

      /** Default 1 second. 0 implies spans are {@link #flush() flushed} externally. */
      Builder flushInterval(int flushInterval);

      /** Sets kafka-topic for zipkin to report to. Default topic zipkin. **/
      Builder topic(String topic);

      Config build();
    }
  }

  private final Producer<byte[], byte[]> producer;

  private final String topic;

  /**
   * Create a new instance with default configuration.
   *
   * @param bootstrapServers A list of host/port pairs to use for establishing the initial connection to the Kafka cluster.
   *                         Like: host1:port1,host2:port2,... Does not to be all the servers part of Kafka cluster.
   * @param metrics Gets notified when spans are accepted or dropped. If you are not interested in
   *                these events you can use {@linkplain EmptySpanCollectorMetricsHandler}
   */
  public static KafkaSpanCollector create(String bootstrapServers, SpanCollectorMetricsHandler metrics) {
    return new KafkaSpanCollector(Config.builder(bootstrapServers).build(), metrics);
  }

  /**
   * @param config includes flush interval and kafka properties
   * @param metrics Gets notified when spans are accepted or dropped. If you are not interested in
   *                these events you can use {@linkplain EmptySpanCollectorMetricsHandler}
   */
  public static KafkaSpanCollector create(Config config, SpanCollectorMetricsHandler metrics) {
    return new KafkaSpanCollector(config, metrics);
  }

  // Visible for testing. Ex when tests need to explicitly control flushing, set interval to 0.
  KafkaSpanCollector(Config config, SpanCollectorMetricsHandler metrics) {
    super(SpanCodec.THRIFT, metrics, config.flushInterval());
    this.producer = new KafkaProducer<>(config.kafkaProperties());
    this.topic = config.topic();
  }

  @Override
  protected void sendSpans(byte[] thrift) throws IOException {
    Future<RecordMetadata> future =
        producer.send(new ProducerRecord<byte[], byte[]>(this.topic, thrift));
    if (future.isCancelled()) throw new IllegalStateException("cancelled sending spans");
  }

  @Override
  public void close() {
    producer.close();
    super.close();
  }
}
