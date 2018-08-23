package brave.kafka.clients;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;

import static brave.kafka.clients.KafkaPropagation.SETTER;
import static brave.kafka.clients.KafkaPropagation.UTF_8;

public class HeadersSetterTest extends PropagationSetterTest<Headers, String> {
  Headers carrier = new RecordHeaders();

  @Override public Propagation.KeyFactory<String> keyFactory() {
    return Propagation.KeyFactory.STRING;
  }

  @Override protected Headers carrier() {
    return carrier;
  }

  @Override protected Propagation.Setter<Headers, String> setter() {
    return SETTER;
  }

  @Override protected Iterable<String> read(Headers carrier, String key) {
    return StreamSupport.stream(carrier.headers(key).spliterator(), false)
        .map(h -> new String(h.value(), UTF_8))
        .collect(Collectors.toList());
  }
}
