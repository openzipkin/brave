/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorSupplier;

class TracingFixedKeyProcessorSupplier<KIn, VIn, VOut>
  implements FixedKeyProcessorSupplier<KIn, VIn, VOut> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final FixedKeyProcessorSupplier<KIn, VIn, VOut> delegateProcessorSupplier;

  TracingFixedKeyProcessorSupplier(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName,
    FixedKeyProcessorSupplier<KIn, VIn, VOut> processorSupplier) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.spanName = spanName;
    this.delegateProcessorSupplier = processorSupplier;
  }

  /** This wraps process method to enable tracing. */
  @Override public FixedKeyProcessor<KIn, VIn, VOut> get() {
    return new TracingFixedKeyProcessor<>(kafkaStreamsTracing, spanName,
      delegateProcessorSupplier.get());
  }
}
