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

import brave.Span;
import brave.Tracer;
import java.util.function.BiFunction;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;

import static brave.internal.Throwables.propagateIfFatal;

public class TracingProcessor<K, V> implements Processor<K, V> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final Processor<K, V> delegateProcessor;
  final BiFunction<K, V, SpanInfo> mkSpan;

  ProcessorContext processorContext;

  public TracingProcessor(KafkaStreamsTracing kafkaStreamsTracing,
    BiFunction<K, V, SpanInfo> mkSpan,
    Processor<K, V> delegateProcessor) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracer;
    this.delegateProcessor = delegateProcessor;
    this.mkSpan = mkSpan;
  }


  @Override
  public void init(ProcessorContext processorContext) {
    this.processorContext = processorContext;
    delegateProcessor.init(processorContext);
  }

  @Override
  public void process(K k, V v) {
    SpanInfo info = mkSpan.apply(k, v);
    Span span = kafkaStreamsTracing.nextSpan(processorContext);
    if (!span.isNoop()) {
      span.name(info.spanName);
      info.annotations.forEach(span::annotate);
      info.tags.forEach(span::tag);
      span.start();
    }

    Tracer.SpanInScope ws = tracer.withSpanInScope(span);
    Throwable error = null;
    try {
      delegateProcessor.process(k, v);
    } catch (Throwable e) {
      error = e;
      propagateIfFatal(e);
      throw e;
    } finally {
      // Inject this span so that the next stage uses it as a parent
      kafkaStreamsTracing.injector.inject(span.context(), processorContext.headers());
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
