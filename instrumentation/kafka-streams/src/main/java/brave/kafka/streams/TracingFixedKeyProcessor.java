/*
 * Copyright 2013-2023 The OpenZipkin Authors
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

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;

import static brave.internal.Throwables.propagateIfFatal;

class TracingFixedKeyProcessor<KIn, VIn, VOut> implements FixedKeyProcessor<KIn, VIn, VOut> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final String spanName;
  final FixedKeyProcessor<KIn, VIn, VOut> delegateProcessor;

  FixedKeyProcessorContext processorContext;

  TracingFixedKeyProcessor(KafkaStreamsTracing kafkaStreamsTracing,
                           String spanName, FixedKeyProcessor<KIn, VIn, VOut> delegateProcessor) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracer;
    this.spanName = spanName;
    this.delegateProcessor = delegateProcessor;
  }

  @Override
  public void init(FixedKeyProcessorContext<KIn, VOut> context) {
    this.processorContext = context;
    delegateProcessor.init(processorContext);
  }

  @Override
  public void process(FixedKeyRecord<KIn, VIn> record) {
    Span span = kafkaStreamsTracing.nextSpan(processorContext, record.headers());
    if (!span.isNoop()) {
      span.name(spanName);
      span.start();
    }

    Tracer.SpanInScope ws = tracer.withSpanInScope(span);
    Throwable error = null;
    try {
      delegateProcessor.process(record);
    } catch (Throwable e) {
      error = e;
      propagateIfFatal(e);
      throw e;
    } finally {
      // Inject this span so that the next stage uses it as a parent
      kafkaStreamsTracing.injector.inject(span.context(), record.headers());
      if (error != null) span.error(error);
      span.finish();
      ws.close();
    }
  }

  @Override
  public void close() {
    delegateProcessor.close();
  }
}
