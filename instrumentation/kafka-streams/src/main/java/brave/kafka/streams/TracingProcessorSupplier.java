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

import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorSupplier;

class TracingProcessorSupplier<K, V> implements ProcessorSupplier<K, V> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final Processor<K, V> delegateProcessor;

  TracingProcessorSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName,
    Processor<K, V> delegateProcessor) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateProcessor = delegateProcessor;
  }

  /** This wraps process method to enable tracing. */
  @Override public Processor<K, V> get() {
    return new TracingProcessor<>(kafkaStreamsTracing, spanName, delegateProcessor);
  }
}
