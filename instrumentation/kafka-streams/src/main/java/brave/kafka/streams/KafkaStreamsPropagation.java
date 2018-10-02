package brave.kafka.streams;

import brave.propagation.Propagation.Getter;
import java.nio.charset.Charset;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

final class KafkaStreamsPropagation {

  static final Charset UTF_8 = Charset.forName("UTF-8");

  static final Getter<Headers, String> GETTER = (carrier, key) -> {
    Header header = carrier.lastHeader(key);
    if (header == null) return null;
    return new String(header.value(), UTF_8);
  };

  KafkaStreamsPropagation() {
  }
}
