package brave.kafka.streams;

import brave.kafka.clients.KafkaTracing;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 * Tagging policy is not yet dynamic. The descriptions below reflect static policy.
 */
final class KafkaStreamsTags {
  /**
   * Added {@link KafkaStreamsTracing#nextSpan(ProcessorContext) processor} spans when the key not null or empty.
   *
   */
  static final String KAFKA_KEY_TAG = "kafka.key";
}
