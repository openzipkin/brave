/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
