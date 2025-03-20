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
import org.apache.kafka.streams.kstream.ValueTransformer;
import org.apache.kafka.streams.kstream.ValueTransformerSupplier;

public class TracingValueTransformerSupplier<V, VR> implements ValueTransformerSupplier<V, VR> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final ValueTransformerSupplier<V, VR> delegateTransformerSupplier;
  final Map<Long, String> annotations;
  final Map<String, String> tags;

  public TracingValueTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName,
    ValueTransformerSupplier<V, VR> delegateTransformerSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateTransformerSupplier = delegateTransformerSupplier;
    this.annotations = Collections.emptyMap();
    this.tags = Collections.emptyMap();
  }

  public TracingValueTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName, Map<Long, String> annotations, Map<String, String> tags,
    ValueTransformerSupplier<V, VR> delegateTransformerSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateTransformerSupplier = delegateTransformerSupplier;
    this.annotations = annotations;
    this.tags = tags;
  }

  /** This wraps transform method to enable tracing. */
  @Override public ValueTransformer<V, VR> get() {
    return new TracingValueTransformer<>(kafkaStreamsTracing, spanName, annotations, tags, delegateTransformerSupplier.get());
  }
}
