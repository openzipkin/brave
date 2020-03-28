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
package brave.internal.propagation;

import brave.Tracer;
import brave.internal.InternalPropagation;
import brave.internal.Nullable;
import brave.internal.PropagationFields;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Internal type that adds correlation fields "traceId", "parentId", "spanId" and "sampled" when a
 * {@linkplain Tracer#currentSpan() span is current}. "traceId" and "spanId" are used in log
 * correlation. "parentId" is used for scenarios such as log parsing that reconstructs the trace
 * tree. "sampled" is used as a hint that a span found in logs might be in Zipkin.
 */
public abstract class CorrelationFieldScopeDecorator implements ScopeDecorator {
  final String[] fieldNames;
  final Getter<TraceContext, String>[] getters;

  protected static abstract class Builder<B extends Builder<B>> {
    final Map<String, Getter<TraceContext, String>> fieldToGetter = new LinkedHashMap<>();

    protected Builder() {
      fieldToGetter.put("traceId", TraceContextGetter.TRACE_ID);
      fieldToGetter.put("parentId", TraceContextGetter.PARENT_ID);
      fieldToGetter.put("spanId", TraceContextGetter.SPAN_ID);
      fieldToGetter.put("sampled", TraceContextGetter.SAMPLED);
    }

    /**
     * Removes a field from the correlation context. This could be a default field you aren't using,
     * such as "parentId", or one added via {@link #addExtraField(String)}.
     *
     * <p><em>Note:</em> If you remove all fields, {@link #build()} will throw an exception.
     *
     * @since 5.11
     */
    public B removeField(String fieldName) {
      if (fieldName == null) throw new NullPointerException("fieldName == null");
      if (fieldName.isEmpty()) throw new NullPointerException("fieldName is empty");
      String lowercase = fieldName.toLowerCase(Locale.ROOT); // contract of extra fields internally
      fieldToGetter.put(lowercase, TraceContextGetter.EXTRA_FIELD);
      return (B) this;
    }

    /**
     * Adds a field from {@link ExtraFieldPropagation#extraKeys()} into the correlation context.
     *
     * <p>It is the responsibility of the caller to verify the field is a valid extra field. Any
     * incorrect fields will be ignored at runtime.
     *
     * @since 5.11
     */
    public B addExtraField(String fieldName) {
      if (fieldName == null) throw new NullPointerException("fieldName == null");
      if (fieldName.isEmpty()) throw new NullPointerException("fieldName is empty");
      String lowercase = fieldName.toLowerCase(Locale.ROOT); // contract of extra fields internally
      fieldToGetter.put(lowercase, TraceContextGetter.EXTRA_FIELD);
      return (B) this;
    }

    /** @throws IllegalArgumentException if all correlation fields were removed. */
    public abstract ScopeDecorator build();
  }

  protected CorrelationFieldScopeDecorator(Builder<?> builder) {
    int fieldCount = builder.fieldToGetter.size();
    if (fieldCount == 0) throw new IllegalArgumentException("no fields");
    fieldNames = new String[fieldCount];
    getters = new Getter[fieldCount];
    int i = 0;
    for (Map.Entry<String, Getter<TraceContext, String>> entry : builder.fieldToGetter.entrySet()) {
      fieldNames[i] = entry.getKey();
      getters[i++] = entry.getValue();
    }
  }

  /**
   * When the input is not null "traceId", "parentId", "spanId" and "sampled" correlation properties
   * are saved off and replaced with those of the current span. When the input is null, these
   * properties are removed. Either way, "traceId", "parentId", "spanId" and "sampled" properties
   * are restored on {@linkplain Scope#close()}.
   */
  @Override public Scope decorateScope(@Nullable TraceContext context, Scope scope) {
    String[] previousValues = new String[getters.length];

    boolean changed = false;
    for (int i = 0; i < getters.length; i++) {
      String fieldName = fieldNames[i];
      String currentValue = context != null ? getters[i].get(context, fieldName) : null;
      String previousValue = get(fieldName);
      if (currentValue != null && !currentValue.equals(previousValue)) {
        put(fieldName, currentValue);
        changed = true;
      } else if (previousValue != null) {
        remove(fieldName);
        changed = true;
      }
      previousValues[i] = previousValue;
    }

    if (!changed) return scope;

    class CorrelationFieldCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        for (int i = 0; i < fieldNames.length; i++) {
          replace(fieldNames[i], previousValues[i]);
        }
      }
    }
    return new CorrelationFieldCurrentTraceContextScope();
  }

  final void replace(String fieldName, @Nullable String value) {
    if (value != null) {
      put(fieldName, value);
    } else {
      remove(fieldName);
    }
  }

  enum TraceContextGetter implements Propagation.Getter<TraceContext, String> {
    TRACE_ID() {
      @Override public String get(TraceContext context, String key) {
        return context.traceIdString();
      }
    },
    PARENT_ID() {
      @Override public String get(TraceContext context, String key) {
        return context.parentIdString();
      }
    },
    SPAN_ID() {
      @Override public String get(TraceContext context, String key) {
        return context.spanIdString();
      }
    },
    SAMPLED() {
      @Override public String get(TraceContext context, String key) {
        Boolean sampled = context.sampled();
        return sampled != null ? sampled.toString() : null;
      }
    },
    EXTRA_FIELD() {
      final Class<? extends PropagationFields<String, String>> propagationType =
        InternalPropagation.instance.extraPropagationFieldsType();

      @Override public String get(TraceContext context, String key) {
        return PropagationFields.get(context, key, propagationType);
      }
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
}
