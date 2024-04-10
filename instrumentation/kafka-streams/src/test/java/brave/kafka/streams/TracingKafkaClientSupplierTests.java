/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import java.util.Collections;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.streams.KafkaClientSupplier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TracingKafkaClientSupplierTests {

  final Map<String, Object> props = Collections.singletonMap("bootstrap.servers", "localhost:9092");

  @Test void shouldReturnNewAdmin() {
    TracingKafkaClientSupplier supplier = new TracingKafkaClientSupplier(null);
    assertThat(supplier.getAdmin(props)).isNotNull();
  }

  @Test void shouldThrowException() {
    assertThrows(UnsupportedOperationException.class, () -> {
      FakeKafkaClientSupplier fake = new FakeKafkaClientSupplier();
      fake.getAdmin(props);
    });
  }

  private static class FakeKafkaClientSupplier implements KafkaClientSupplier {

    @Override
    public Producer<byte[], byte[]> getProducer(Map<String, Object> map) {
      return null;
    }

    @Override
    public Consumer<byte[], byte[]> getConsumer(Map<String, Object> map) {
      return null;
    }

    @Override
    public Consumer<byte[], byte[]> getRestoreConsumer(Map<String, Object> map) {
      return null;
    }

    @Override
    public Consumer<byte[], byte[]> getGlobalConsumer(Map<String, Object> map) {
      return null;
    }
  }
}
