package brave.kafka.clients;

import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import java.nio.charset.Charset;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import static brave.propagation.B3SingleFormat.writeB3SingleFormat;

final class KafkaPropagation {

  static final Charset UTF_8 = Charset.forName("UTF-8");

  static final TraceContext TEST_CONTEXT = TraceContext.newBuilder().traceId(1L).spanId(1L).build();
  static final ProducerRecord<String, String> TEST_RECORD = new ProducerRecord<>("dummy", "");
  static final Headers B3_SINGLE_TEST_HEADERS =
      TEST_RECORD.headers().add("b3", writeB3SingleFormat(TEST_CONTEXT).getBytes(UTF_8));

  static final Setter<Headers, String> HEADERS_SETTER = (carrier, key, value) -> {
    carrier.remove(key);
    carrier.add(key, value.getBytes(UTF_8));
  };

  static final Getter<Headers, String> HEADERS_GETTER = (carrier, key) -> {
    Header header = carrier.lastHeader(key);
    if (header == null || header.value() == null) return null;
    return new String(header.value(), UTF_8);
  };

  KafkaPropagation() {
  }
}
