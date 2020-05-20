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
package brave.features.opentracing;

import brave.Span.Kind;
import brave.Tracing;
import brave.baggage.BaggagePropagation;
import brave.features.opentracing.TextMapPropagation.TextMapExtractor;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapExtract;
import io.opentracing.propagation.TextMapInject;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class BraveTracer implements Tracer {
  static BraveTracer wrap(brave.Tracing tracing) {
    if (tracing == null) throw new NullPointerException("tracing == null");
    return new BraveTracer(tracing);
  }

  final Tracing tracing;
  final brave.Tracer tracer;
  final Injector<TextMapInject> injector, clientInjector, producerInjector, consumerInjector;
  // In OpenTracing we can't tell from the format or the carrier, which span kind the extract is for
  final Extractor<TextMapExtract> extractor;

  BraveTracer(brave.Tracing tracing) {
    this.tracing = tracing;
    tracer = tracing.tracer();
    Set<String> lcPropagationKeys = new LinkedHashSet<>();
    for (String keyName : BaggagePropagation.allKeyNames(tracing.propagation())) {
      lcPropagationKeys.add(keyName.toLowerCase(Locale.ROOT));
    }
    injector = tracing.propagation().injector(TextMapPropagation.SETTER);
    clientInjector = tracing.propagation().injector(TextMapPropagation.REMOTE_SETTER.CLIENT);
    producerInjector = tracing.propagation().injector(TextMapPropagation.REMOTE_SETTER.PRODUCER);
    consumerInjector = tracing.propagation().injector(TextMapPropagation.REMOTE_SETTER.CONSUMER);
    extractor =
      new TextMapExtractor(tracing.propagation(), lcPropagationKeys, TextMapPropagation.GETTER);
  }

  @Override public ScopeManager scopeManager() {
    return null; // out-of-scope for a simple example
  }

  @Override public Span activeSpan() {
    return null; // out-of-scope for a simple example
  }

  @Override public Scope activateSpan(Span span) {
    return null; // out-of-scope for a simple example
  }

  @Override public BraveSpanBuilder buildSpan(String operationName) {
    return new BraveSpanBuilder(tracer, operationName);
  }

  @Override public <C> void inject(SpanContext spanContext, Format<C> format, C carrier) {
    if (!(carrier instanceof TextMapInject)) {
      throw new UnsupportedOperationException(carrier + " not instanceof TextMapInject");
    }
    BraveSpanContext braveContext = ((BraveSpanContext) spanContext);
    Kind kind = braveContext.kind;
    Injector<TextMapInject> injector;
    if (Kind.CLIENT.equals(kind)) {
      injector = clientInjector;
    } else if (Kind.PRODUCER.equals(kind)) {
      injector = producerInjector;
    } else if (Kind.CONSUMER.equals(kind)) {
      injector = consumerInjector;
    } else {
      injector = this.injector;
    }
    injector.inject(braveContext.context, (TextMapInject) carrier);
  }

  @Override public <C> BraveSpanContext extract(Format<C> format, C carrier) {
    if (!(carrier instanceof TextMapExtract)) {
      throw new UnsupportedOperationException(carrier + " not instanceof TextMapExtract");
    }
    TraceContextOrSamplingFlags extractionResult = extractor.extract((TextMapExtract) carrier);
    return BraveSpanContext.create(extractionResult);
  }

  @Override public void close() {
    tracing.close();
  }
}
