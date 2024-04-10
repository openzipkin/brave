/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;

final class TracingFixedKeyProcessor<KIn, VIn, VOut> extends
  BaseTracingProcessor<FixedKeyProcessorContext<KIn, VOut>, FixedKeyRecord<KIn, VIn>, FixedKeyProcessor<KIn, VIn, VOut>>
  implements FixedKeyProcessor<KIn, VIn, VOut> {

  TracingFixedKeyProcessor(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
    FixedKeyProcessor<KIn, VIn, VOut> delegate) {
    super(kafkaStreamsTracing, spanName, delegate);
  }

  @Override Headers headers(FixedKeyRecord<KIn, VIn> record) {
    return record.headers();
  }

  @Override void process(FixedKeyProcessor<KIn, VIn, VOut> delegate,
    FixedKeyRecord<KIn, VIn> record) {
    delegate.process(record);
  }

  @Override public void init(FixedKeyProcessorContext<KIn, VOut> context) {
    this.context = context;
    CurrentTraceContext current =
      kafkaStreamsTracing.kafkaTracing.messagingTracing().tracing().currentTraceContext();
    TraceContext traceContext = current.get();
    if (traceContext != null) {
      context =
        new TracingFixedKeyProcessorContext<>(context, kafkaStreamsTracing.injector, traceContext);
    }
    delegate.init(context);
  }

  @Override public void close() {
    delegate.close();
  }
}
