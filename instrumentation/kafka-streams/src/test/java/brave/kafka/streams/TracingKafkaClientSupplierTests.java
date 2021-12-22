/*
 * Copyright 2013-2021 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
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

  final Map<String, Object> props = Collections.singletonMap("bootstrap.servers","localhost:9092");

  @Test
  public void shouldReturnNewAdminClient() {
    TracingKafkaClientSupplier supplier = new TracingKafkaClientSupplier(null);
    assertThat(supplier.getAdminClient(props)).isNotNull();
    assertThat(supplier.getAdmin(props)).isNotNull();
  }

  @Test
  void shouldThrowException() {
    assertThrows(UnsupportedOperationException.class, () -> {
      FakeKafkaClientSupplier fake = new FakeKafkaClientSupplier();
      fake.getAdminClient(props);
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
