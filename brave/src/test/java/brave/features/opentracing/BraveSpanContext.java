/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.features.opentracing;

import brave.Span;
import brave.baggage.BaggageField;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.opentracing.SpanContext;
import java.util.Collections;
import java.util.Map;

class BraveSpanContext implements SpanContext {
  static BraveSpanContext create(TraceContextOrSamplingFlags extractionResult) {
    return extractionResult.context() != null
      ? new BraveSpanContext(extractionResult.context())
      : new BraveSpanContext.Incomplete(extractionResult);
  }

  @Nullable final TraceContext context;
  volatile Span.Kind kind;

  BraveSpanContext(TraceContext context) {
    this.context = context;
  }

  @Override public String toTraceId() {
    return context != null ? context.traceIdString() : null;
  }

  @Override public String toSpanId() {
    return context != null ? context.spanIdString() : null;
  }

  @Override public Iterable<Map.Entry<String, String>> baggageItems() {
    if (context == null) return Collections.emptyList();
    return BaggageField.getAllValues(context).entrySet();
  }

  static final class Incomplete extends BraveSpanContext {
    final TraceContextOrSamplingFlags extractionResult;

    Incomplete(TraceContextOrSamplingFlags extractionResult) {
      super(extractionResult.context());
      this.extractionResult = extractionResult;
    }
  }
}
