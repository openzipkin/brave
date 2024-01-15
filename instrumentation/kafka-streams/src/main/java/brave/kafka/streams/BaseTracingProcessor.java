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

import brave.Span;
import brave.Tracer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.streams.processor.api.ProcessingContext;

import static brave.internal.Throwables.propagateIfFatal;

abstract class BaseTracingProcessor<C extends ProcessingContext, R, P> {
  final KafkaStreamsTracing kafkaStreamsTracing;
  final Tracer tracer;
  final String spanName;
  final P delegate;
  C context;

  BaseTracingProcessor(KafkaStreamsTracing kafkaStreamsTracing, String spanName, P delegate) {
    this.kafkaStreamsTracing = kafkaStreamsTracing;
    this.tracer = kafkaStreamsTracing.tracer;
    this.spanName = spanName;
    this.delegate = delegate;
  }

  abstract Headers headers(R record);

  abstract void process(P delegate, R record);

  public void process(R record) {
    Span span = kafkaStreamsTracing.nextSpan(context, headers(record));
    if (!span.isNoop()) {
      span.name(spanName);
      span.start();
    }

    Tracer.SpanInScope scope = tracer.withSpanInScope(span);
    Throwable error = null;
    try {
      process(delegate, record);
    } catch (Throwable e) {
      error = e;
      propagateIfFatal(e);
      throw e;
    } finally {
      // Inject this span so that the next stage uses it as a parent
      kafkaStreamsTracing.injector.inject(span.context(), headers(record));
      if (error != null) span.error(error);
      span.finish();
      scope.close();
    }
  }
}
