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

import java.util.Collections;
import java.util.Map;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;

public class TracingValueTransformerWithKeySupplier<K, V, VR> implements
  ValueTransformerWithKeySupplier<K, V, VR> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final ValueTransformerWithKeySupplier<K, V, VR> delegateTransformerSupplier;
  final Map<Long, String> annotations;
  final Map<String, String> tags;

  public TracingValueTransformerWithKeySupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName,
    ValueTransformerWithKeySupplier<K, V, VR> delegateTransformerSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateTransformerSupplier = delegateTransformerSupplier;
    this.annotations = Collections.emptyMap();
    this.tags = Collections.emptyMap();
  }

  public TracingValueTransformerWithKeySupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName, Map<Long, String> annotations, Map<String, String> tags,
    ValueTransformerWithKeySupplier<K, V, VR> delegateTransformerSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateTransformerSupplier = delegateTransformerSupplier;
    this.annotations = Collections.emptyMap();
    this.tags = Collections.emptyMap();
  }

  /** This wraps transform method to enable tracing. */
  @Override public ValueTransformerWithKey<K, V, VR> get() {
    return new TracingValueTransformerWithKey<>(kafkaStreamsTracing, spanName, delegateTransformerSupplier.get());
  }
}
