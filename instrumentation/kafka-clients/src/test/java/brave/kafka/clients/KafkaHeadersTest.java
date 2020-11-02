/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
package brave.kafka.clients;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaHeadersTest {
  ConsumerRecord<String, String> record = new ConsumerRecord<>("top", 0, 1, "key", "value");

  @Test public void lastStringHeader() {
    record.headers().add("b3", new byte[] {'1'});

    assertThat(KafkaHeaders.lastStringHeader(record.headers(), "b3"))
        .isEqualTo("1");
  }

  @Test public void lastStringHeader_null() {
    assertThat(KafkaHeaders.lastStringHeader(record.headers(), "b3")).isNull();
  }

  @Test public void replaceHeader() {
    KafkaHeaders.replaceHeader(record.headers(), "b3", "1");

    assertThat(record.headers().lastHeader("b3").value())
        .containsExactly('1');
  }

  @Test public void replaceHeader_replace() {
    record.headers().add("b3", new byte[0]);
    KafkaHeaders.replaceHeader(record.headers(), "b3", "1");

    assertThat(record.headers().lastHeader("b3").value())
        .containsExactly('1');
  }

  @Test public void replaceHeader_readonly() {
    ((RecordHeaders) record.headers()).setReadOnly();
    KafkaHeaders.replaceHeader(record.headers(), "b3", "1");
  }
}
