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
package brave.kafka.streams;

import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.processor.ProcessorContext;

/**
 * Tagging policy is not yet dynamic. The descriptions below reflect static policy.
 */
class KafkaStreamsTags {
  /**
   * Added on {@link KafkaStreamsTracing#nextSpan(ProcessorContext)} when the key not null or
   * empty.
   */
  static final String KAFKA_STREAMS_APPLICATION_ID_TAG = "kafka.streams.application.id";
  static final String KAFKA_STREAMS_TASK_ID_TAG = "kafka.streams.task.id";
  /**
   * Added on {@link KafkaStreamsTracing#nextSpan(ProcessorContext)} by the {@link
   * KafkaStreamsTracing#filter(String, Predicate)} transformer. The tag value is <code>true</code>
   * when the message is filtered out, <code>false</code> otherwise.
   */
  static final String KAFKA_STREAMS_FILTERED_TAG = "kafka.streams.filtered";
}
