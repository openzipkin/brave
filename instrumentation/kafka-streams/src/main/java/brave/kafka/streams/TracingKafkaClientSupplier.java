package brave.kafka.streams;

import brave.kafka.clients.KafkaTracing;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.streams.KafkaClientSupplier;

/**
 * Provides tracing-enabled {@link Consumer} and {@link Producer}
 *
 * @see KafkaTracing
 */
final class TracingKafkaClientSupplier implements KafkaClientSupplier {

  final KafkaTracing kafkaTracing;

  TracingKafkaClientSupplier(KafkaTracing kafkaTracing) {
    this.kafkaTracing = kafkaTracing;
  }

  @Override public AdminClient getAdminClient(Map<String, Object> config) {
    return AdminClient.create(config);
  }

  @Override public Producer<byte[], byte[]> getProducer(Map<String, Object> config) {
    config.put("key.serializer", ByteArraySerializer.class);
    config.put("value.serializer", ByteArraySerializer.class);
    Producer<byte[], byte[]> producer = new KafkaProducer<>(config);
    return kafkaTracing.producer(producer);
  }

  @Override public Consumer<byte[], byte[]> getConsumer(Map<String, Object> config) {
    config.put("key.deserializer", ByteArrayDeserializer.class);
    config.put("value.deserializer", ByteArrayDeserializer.class);
    Consumer<byte[], byte[]> consumer = new KafkaConsumer<>(config);
    return kafkaTracing.consumer(consumer);
  }

  @Override public Consumer<byte[], byte[]> getRestoreConsumer(Map<String, Object> config) {
    return getConsumer(config);
  }

  @Override public Consumer<byte[], byte[]> getGlobalConsumer(Map<String, Object> config) {
    return getConsumer(config);
  }
}

