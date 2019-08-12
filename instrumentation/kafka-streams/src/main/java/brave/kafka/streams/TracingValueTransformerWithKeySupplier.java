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

import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;

class TracingValueTransformerWithKeySupplier<K, V, VR> implements
  ValueTransformerWithKeySupplier<K, V, VR> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final ValueTransformerWithKey<K, V, VR> delegateTransformer;

  TracingValueTransformerWithKeySupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName,
    ValueTransformerWithKey<K, V, VR> delegateTransformer) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateTransformer = delegateTransformer;
  }

  /** This wraps transform method to enable tracing. */
  @Override public ValueTransformerWithKey<K, V, VR> get() {
    return new TracingValueTransformerWithKey<>(kafkaStreamsTracing, spanName, delegateTransformer);
  }
}
