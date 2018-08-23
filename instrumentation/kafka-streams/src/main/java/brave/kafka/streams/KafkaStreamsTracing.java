package brave.kafka.streams;

import brave.kafka.clients.KafkaTracing;
import org.apache.kafka.streams.KafkaClientSupplier;

/** Use this class to decorate your Kafka Streams client. */
public final class KafkaStreamsTracing {

  public static KafkaStreamsTracing create(KafkaTracing kafkaTracing) {
    return new KafkaStreamsTracing(kafkaTracing);
  }

  final KafkaTracing kafkaTracing;

  KafkaStreamsTracing(KafkaTracing kafkaTracing) { // intentionally hidden constructor
    this.kafkaTracing = kafkaTracing;
  }

  /** Returns a client supplier which traces send and receive operations. */
  public KafkaClientSupplier kafkaClientSupplier() {
    return new TracingKafkaClientSupplier(kafkaTracing);
  }
}
