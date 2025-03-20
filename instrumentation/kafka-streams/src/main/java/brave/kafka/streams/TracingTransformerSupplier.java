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

import java.util.Map;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.kstream.TransformerSupplier;

public class TracingTransformerSupplier<K, V, R> implements TransformerSupplier<K, V, R> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final TransformerSupplier<K, V, R> delegateTransformerSupplier;
  final SpanInfo spanInfo;
  public TracingTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName,
    TransformerSupplier<K, V, R> delegateTransformerSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.delegateTransformerSupplier = delegateTransformerSupplier;
    this.spanInfo = new SpanInfo(spanName);
  }
  public TracingTransformerSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName, Map<Long, String> annotations, Map<String, String> tags,
    TransformerSupplier<K, V, R> delegateTransformerSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.delegateTransformerSupplier = delegateTransformerSupplier;
    this.spanInfo = new SpanInfo(spanName, annotations, tags);
    }

  public SpanInfo getSpanInfo(K k, V v) {
    return this.spanInfo;
  }
  /** This wraps transform method to enable tracing. */
  @Override public Transformer<K, V, R> get() {
    return new TracingTransformer<>(kafkaStreamsTracing, this::getSpanInfo, delegateTransformerSupplier.get());
  }
}
