/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaProducerRequestTest {
  ProducerRecord<String, String> record = new ProducerRecord<>("top", "key", "value");
  KafkaProducerRequest request = new KafkaProducerRequest(record);

  @Test void operation() {
    assertThat(request.operation()).isEqualTo("send");
  }

  @Test void topic() {
    assertThat(request.channelKind()).isEqualTo("topic");
    assertThat(request.channelName()).isEqualTo(record.topic());
  }
}
