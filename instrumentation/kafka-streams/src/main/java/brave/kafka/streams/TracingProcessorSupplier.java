/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorSupplier;

public class TracingProcessorSupplier<K, V> implements ProcessorSupplier<K, V> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final SpanInfo spanInfo;
  final ProcessorSupplier<K, V> delegateProcessorSupplier;

  public TracingProcessorSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName,
    ProcessorSupplier<K, V> processorSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanInfo = new SpanInfo(spanName);
    this.delegateProcessorSupplier = processorSupplier;
  }
  public TracingProcessorSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName, Map<Long, String> annotations, Map<String, String> tags,
    ProcessorSupplier<K, V> processorSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.delegateProcessorSupplier = processorSupplier;
    this.spanInfo = new SpanInfo(spanName, annotations, tags);
  }

  public SpanInfo getSpanInfo(K k, V v) {
    return this.spanInfo;
  }

  /** This wraps process method to enable tracing. */
  @Override public Processor<K, V> get() {
    return new TracingProcessor<>(kafkaStreamsTracing, this::getSpanInfo, delegateProcessorSupplier.get());
  }
}
