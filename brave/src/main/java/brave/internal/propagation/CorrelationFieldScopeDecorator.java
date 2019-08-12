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
package brave.internal.propagation;

import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;

/**
 * Adds correlation properties "traceId", "parentId", "spanId" and "sampled" when a {@link
 * brave.Tracer#currentSpan() span is current}. "traceId" and "spanId" are used in log correlation.
 * "parentId" is used for scenarios such as log parsing that reconstructs the trace tree. "sampled"
 * is used as a hint that a span found in logs might be in Zipkin.
 */
public abstract class CorrelationFieldScopeDecorator implements ScopeDecorator {

  /**
   * When the input is not null "traceId", "parentId", "spanId" and "sampled" correlation properties
   * are saved off and replaced with those of the current span. When the input is null, these
   * properties are removed. Either way, "traceId", "parentId", "spanId" and "sampled" properties
   * are restored on {@linkplain Scope#close()}.
   */
  @Override public Scope decorateScope(@Nullable TraceContext currentSpan, Scope scope) {
    String previousTraceId = get("traceId");
    String previousSpanId = get("spanId");
    String previousParentId = get("parentId");
    String previousSampled = get("sampled");

    if (currentSpan != null) {
      maybeReplaceTraceContext(
        currentSpan, previousTraceId, previousParentId, previousSpanId, previousSampled);
    } else {
      remove("traceId");
      remove("parentId");
      remove("spanId");
      remove("sampled");
    }

    class CorrelationFieldCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        replace("traceId", previousTraceId);
        replace("parentId", previousParentId);
        replace("spanId", previousSpanId);
        replace("sampled", previousSampled);
      }
    }
    return new CorrelationFieldCurrentTraceContextScope();
  }

  /**
   * Idempotently sets correlation properties to hex representation of trace identifiers in this
   * context.
   */
  void maybeReplaceTraceContext(
    TraceContext currentSpan,
    String previousTraceId,
    @Nullable String previousParentId,
    String previousSpanId,
    @Nullable String previousSampled
  ) {
    String traceId = currentSpan.traceIdString();
    if (!traceId.equals(previousTraceId)) put("traceId", currentSpan.traceIdString());

    String parentId = currentSpan.parentIdString();
    if (parentId == null) {
      remove("parentId");
    } else {
      boolean sameParentId = parentId.equals(previousParentId);
      if (!sameParentId) put("parentId", parentId);
    }

    String spanId = currentSpan.spanIdString();
    if (!spanId.equals(previousSpanId)) put("spanId", spanId);

    Boolean sampled = currentSpan.sampled();
    if (sampled == null) {
      remove("sampled");
    } else {
      String sampledString = sampled.toString();
      boolean sameSampled = sampledString.equals(previousSampled);
      if (!sameSampled) put("sampled", sampledString);
    }
  }

  /**
   * Returns the correlation property of the specified name iff it is a string, or null otherwise.
   */
  protected abstract @Nullable String get(String key);

  /** Replaces the correlation property of the specified name */
  protected abstract void put(String key, String value);

  /** Removes the correlation property of the specified name */
  protected abstract void remove(String key);

  final void replace(String key, @Nullable String value) {
    if (value != null) {
      put(key, value);
    } else {
      remove(key);
    }
  }
}
