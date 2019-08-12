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
package brave.features.opentracing;

import brave.propagation.ExtraFieldPropagation;
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

  final TraceContext context;

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
    return ExtraFieldPropagation.getAll(context).entrySet();
  }

  static final class Incomplete extends BraveSpanContext {
    final TraceContextOrSamplingFlags extractionResult;

    Incomplete(TraceContextOrSamplingFlags extractionResult) {
      super(extractionResult.context());
      this.extractionResult = extractionResult;
    }
  }
}
