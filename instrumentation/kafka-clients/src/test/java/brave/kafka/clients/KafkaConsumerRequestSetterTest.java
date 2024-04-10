/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import brave.propagation.Propagation;
import brave.test.propagation.PropagationSetterTest;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import static java.nio.charset.StandardCharsets.UTF_8;

public class KafkaConsumerRequestSetterTest extends PropagationSetterTest<KafkaConsumerRequest> {
  KafkaConsumerRequest request = new KafkaConsumerRequest(
    new ConsumerRecord<>("topic", 0, 1L, "key", "value")
  );

  @Override protected KafkaConsumerRequest request() {
    return request;
  }

  @Override protected Propagation.Setter<KafkaConsumerRequest, String> setter() {
    return KafkaConsumerRequest.SETTER;
  }

  @Override protected Iterable<String> read(KafkaConsumerRequest request, String key) {
    return StreamSupport.stream(request.delegate.headers().headers(key).spliterator(), false)
      .map(h -> new String(h.value(), UTF_8))
      .collect(Collectors.toList());
  }
}
