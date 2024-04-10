/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaHeadersTest {
  ConsumerRecord<String, String> record = new ConsumerRecord<>("top", 0, 1, "key", "value");

  @Test void lastStringHeader() {
    record.headers().add("b3", new byte[] {'1'});

    assertThat(KafkaHeaders.lastStringHeader(record.headers(), "b3"))
      .isEqualTo("1");
  }

  @Test void lastStringHeader_null() {
    assertThat(KafkaHeaders.lastStringHeader(record.headers(), "b3")).isNull();
  }

  @Test void replaceHeader() {
    KafkaHeaders.replaceHeader(record.headers(), "b3", "1");

    assertThat(record.headers().lastHeader("b3").value())
      .containsExactly('1');
  }

  @Test void replaceHeader_replace() {
    record.headers().add("b3", new byte[0]);
    KafkaHeaders.replaceHeader(record.headers(), "b3", "1");

    assertThat(record.headers().lastHeader("b3").value())
      .containsExactly('1');
  }
}
