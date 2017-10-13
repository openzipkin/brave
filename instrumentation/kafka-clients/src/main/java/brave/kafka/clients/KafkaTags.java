package brave.kafka.clients;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;

/**
 * Tagging policy is not yet dynamic. The descriptions below reflect static policy.
 */
final class KafkaTags {
  /**
   * Added on {@link KafkaTracing#producer(Producer) producer} and {@link
   * KafkaTracing#nextSpan(ConsumerRecord) processor} spans when the key not null or empty.
   *
   * <p><em>Note:</em> this is not added on {@link KafkaTracing#consumer(Consumer) consumer} spans
   * as they represent a bulk task (potentially multiple keys).
   */
  static final String KAFKA_KEY_TAG = "kafka.key";
  static final String KAFKA_TOPIC_TAG = "kafka.topic";
}
