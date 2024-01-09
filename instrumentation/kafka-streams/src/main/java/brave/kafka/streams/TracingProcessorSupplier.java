/*
 * Copyright 2013-2024 The OpenZipkin Authors
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

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;

class TracingProcessorSupplier<KIn, VIn, KOut, VOut>
  implements ProcessorSupplier<KIn, VIn, KOut, VOut> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final ProcessorSupplier<KIn, VIn, KOut, VOut> delegateProcessorSupplier;

  TracingProcessorSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName,
    ProcessorSupplier<KIn, VIn, KOut, VOut> processorSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateProcessorSupplier = processorSupplier;
  }

  /** This wraps process method to enable tracing. */
  @Override public Processor<KIn, VIn, KOut, VOut> get() {
    return new TracingProcessor<>(kafkaStreamsTracing, spanName, delegateProcessorSupplier.get());
  }
}
