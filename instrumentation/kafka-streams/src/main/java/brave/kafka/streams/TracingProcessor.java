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

import brave.Span;
import brave.Tracer;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

class TracingProcessor<K, V> implements Processor<K, V> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final String spanName;
  final Processor<K, V> delegateProcessor;

  ProcessorContext processorContext;

  TracingProcessor(KafkaStreamsTracing kafkaStreamsTracing,
    String spanName, Processor<K, V> delegateProcessor) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracing.tracer();
    this.spanName = spanName;
    this.delegateProcessor = delegateProcessor;
  }

  @Override
  public void init(ProcessorContext processorContext) {
    this.processorContext = processorContext;
    delegateProcessor.init(processorContext);
  }

  @Override
  public void process(K k, V v) {
    Span span = kafkaStreamsTracing.nextSpan(processorContext);
    if (!span.isNoop()) {
      span.name(spanName);
      span.start();
    }

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      delegateProcessor.process(k, v);
      kafkaStreamsTracing.injector.inject(span.context(), processorContext.headers());
    } catch (RuntimeException | Error e) {
      span.error(e); // finish as an exception means the callback won't finish the span
      throw e;
    } finally {
      span.finish();
    }
  }

  @Override
  public void close() {
    delegateProcessor.close();
  }
}
