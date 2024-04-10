/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
