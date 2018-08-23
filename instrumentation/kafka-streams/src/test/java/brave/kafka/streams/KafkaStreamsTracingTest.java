package brave.kafka.streams;

import brave.Tracing;
import brave.kafka.clients.KafkaTracing;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaStreamsTracingTest {

  Tracing tracing = Tracing.newBuilder().build();
  KafkaStreamsTracing kafkaStreamsTracing =
      KafkaStreamsTracing.create(KafkaTracing.create(tracing));

  @Test public void kafkaClientSupplier_getProducer() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    assertThat(kafkaStreamsTracing.kafkaClientSupplier().getProducer(config)).isNotNull();
  }

  @Test public void kafkaClientSupplier_getConsumer() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("bootstrap.servers", "localhost:9092");
    assertThat(kafkaStreamsTracing.kafkaClientSupplier().getConsumer(config)).isNotNull();
  }
}
