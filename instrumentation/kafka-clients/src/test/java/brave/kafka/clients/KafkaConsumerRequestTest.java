/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.clients;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaConsumerRequestTest {
  ConsumerRecord<String, String> record = new ConsumerRecord<>("top", 0, 1, "key", "value");
  KafkaConsumerRequest request = new KafkaConsumerRequest(record);

  @Test void operation() {
    assertThat(request.operation()).isEqualTo("receive");
  }

  @Test void topic() {
    assertThat(request.channelKind()).isEqualTo("topic");
    assertThat(request.channelName()).isEqualTo(record.topic());
  }
}
