/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.kafka.streams;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

final class TracingProcessor<KIn, VIn, KOut, VOut> extends
  BaseTracingProcessor<ProcessorContext<KOut, VOut>, Record<KIn, VIn>, Processor<KIn, VIn, KOut, VOut>>
  implements Processor<KIn, VIn, KOut, VOut> {

  TracingProcessor(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
    Processor<KIn, VIn, KOut, VOut> delegate) {
    super(kafkaStreamsTracing, spanName, delegate);
  }

  @Override Headers headers(Record<KIn, VIn> record) {
    return record.headers();
  }

  @Override void process(Processor<KIn, VIn, KOut, VOut> delegate, Record<KIn, VIn> record) {
    delegate.process(record);
  }

  @Override public void init(ProcessorContext<KOut, VOut> context) {
    this.context = context;
    CurrentTraceContext current =
      kafkaStreamsTracing.kafkaTracing.messagingTracing().tracing().currentTraceContext();
    TraceContext traceContext = current.get();
    if (traceContext != null) {
      context = new TracingProcessorContext<>(context, kafkaStreamsTracing.injector, traceContext);
    }
    delegate.init(context);
  }

  @Override public void close() {
    delegate.close();
  }
}
