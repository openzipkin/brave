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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;

/**
 * Tagging policy is not yet dynamic. The descriptions below reflect static policy.
 */
final class KafkaTags {
  /**
   * Added on {@link KafkaTracing#producer(Producer) producer} and {@link
   * KafkaTracing#nextSpan(ConsumerRecord) processor} spans when the key not null or empty.
   *
   * <p><em>Note:</em> this is not added on {@link KafkaTracing#consumer(Consumer) consumer} spans
   * as they represent a bulk task (potentially multiple keys).
   */
  static final String KAFKA_KEY_TAG = "kafka.key";
  static final String KAFKA_TOPIC_TAG = "kafka.topic";
}
