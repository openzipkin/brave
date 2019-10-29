/*
 * Copyright 2013-2019 The OpenZipkin Authors
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

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaProducerRequestTest {
  ProducerRecord<String, String> record = new ProducerRecord<>("top", "key", "value");
  KafkaProducerRequest request = new KafkaProducerRequest(record);

  @Test public void operation() {
    assertThat(request.operation()).isEqualTo("send");
  }

  @Test public void topic() {
    assertThat(request.channelKind()).isEqualTo("topic");
    assertThat(request.channelName()).isEqualTo(record.topic());
  }

  @Test public void getHeader() {
    record.headers().add("b3", new byte[] {'1'});

    assertThat(request.getHeader("b3"))
      .isEqualTo("1");
  }

  @Test public void getHeader_null() {
    assertThat(request.getHeader("b3")).isNull();
  }

  @Test public void setHeader() {
    request.setHeader("b3", "1");

    assertThat(record.headers().lastHeader("b3").value())
      .containsExactly('1');
  }
}
