package brave.kafka.clients;

import brave.propagation.Propagation;
import java.nio.charset.Charset;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

final class KafkaPropagation {

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private KafkaPropagation() {
  }

  static final class ProducerRecordSetter implements Propagation.Setter<ProducerRecord, String> {
    @Override public void put(ProducerRecord carrier, String key, String value) {
      carrier.headers().add(key, value.getBytes(UTF_8));
    }
  }

  static final class ConsumerRecordSetter implements Propagation.Setter<ConsumerRecord, String> {
    @Override public void put(ConsumerRecord carrier, String key, String value) {
      carrier.headers().add(key, value.getBytes(UTF_8));
    }
  }

  static final class ConsumerRecordGetter implements Propagation.Getter<ConsumerRecord, String> {
    @Override public String get(ConsumerRecord carrier, String key) {
      Header header = carrier.headers().lastHeader(key);
      if (header == null) return null;
      return new String(header.value(), UTF_8);
    }
  }
}
