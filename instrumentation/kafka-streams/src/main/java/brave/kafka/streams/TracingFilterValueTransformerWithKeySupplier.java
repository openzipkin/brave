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
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier;

public class TracingFilterValueTransformerWithKeySupplier<K, V> implements
  ValueTransformerWithKeySupplier<K, V, V> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final Predicate<K, V> delegatePredicate;
  final boolean filterNot;

  public TracingFilterValueTransformerWithKeySupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName, Predicate<K, V> delegatePredicate, boolean filterNot) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegatePredicate = delegatePredicate;
    this.filterNot = filterNot;
  }

  @Override public ValueTransformerWithKey<K, V, V> get() {
    return new TracingFilterValueTransformerWithKey<>(kafkaStreamsTracing, spanName,
      delegatePredicate,
      filterNot);
  }
}
