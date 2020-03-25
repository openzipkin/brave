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
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.processor.ProcessorContext;

import static brave.kafka.streams.KafkaStreamsTags.KAFKA_STREAMS_FILTERED_TAG;
import static zipkin2.Call.propagateIfFatal;

abstract class TracingFilter<K, V, R> {

  final KafkaStreamsTracing kafkaStreamsTracing;
  final String spanName;
  final Predicate<K, V> delegatePredicate;
  final Tracer tracer;
  final boolean filterNot;
  ProcessorContext processorContext;

  TracingFilter(KafkaStreamsTracing kafkaStreamsTracing, String spanName,
    Predicate<K, V> delegatePredicate, boolean filterNot) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracer;
    this.spanName = spanName;
    this.delegatePredicate = delegatePredicate;
    this.filterNot = filterNot;
  }

  public void init(ProcessorContext context) {
    processorContext = context;
  }

  public R transform(K key, V value) {
    Span span = kafkaStreamsTracing.nextSpan(processorContext);
    if (!span.isNoop()) {
      span.name(spanName);
      span.start();
    }

    Tracer.SpanInScope ws = tracer.withSpanInScope(span);
    Throwable error = null;
    try {
      if (filterNot ^ delegatePredicate.test(key, value)) {
        span.tag(KAFKA_STREAMS_FILTERED_TAG, "false");
        return result(key, value);
      } else {
        span.tag(KAFKA_STREAMS_FILTERED_TAG, "true");
        return null; // meaning KV pair will not be forwarded thus effectively filtered
      }
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

  abstract R result(K key, V value);
}
