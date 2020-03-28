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
import brave.propagation.TraceContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Adds correlation fields such as "traceId", "parentId", "spanId" and "sampled" when a {@linkplain
 * Tracer#currentSpan() span is current}. "traceId" and "spanId" are used in log correlation.
 * "parentId" is used for scenarios such as log parsing that reconstructs the trace tree. "sampled"
 * is used as a hint that a span found in logs might be in Zipkin.
 */
public abstract class CorrelationFieldScopeDecoratorBuilder<B extends CorrelationFieldScopeDecoratorBuilder<B>> {
  final Context context;
  final Map<String, Getter> fieldToGetter = new LinkedHashMap<>();

  protected CorrelationFieldScopeDecoratorBuilder(Context context) {
    this.context = context;
    fieldToGetter.put("traceId", TraceContextGetter.TRACE_ID);
    fieldToGetter.put("parentId", TraceContextGetter.PARENT_ID);
    fieldToGetter.put("spanId", TraceContextGetter.SPAN_ID);
    fieldToGetter.put("sampled", TraceContextGetter.SAMPLED);
  }

  /**
   * Removes a field from the correlation context. This could be a default field you aren't using,
   * such as "parentId", or one added via {@link #addExtraField(String)}.
   *
   * <p>Here are the default field names:
   * <ul>
   *   <li>traceId</li>
   *   <li>parentId</li>
   *   <li>spanId</li>
   *   <li>sampled</li>
   * </ul>
   *
   * <p><em>Note:</em> If you remove all fields, {@link #build()} will throw an exception.
   *
   * @since 5.11
   */
  public B removeField(String name) {
    if (name == null) throw new NullPointerException("name == null");
    if (name.isEmpty()) throw new NullPointerException("name is empty");
    // this is allowed to temporarily become empty in support of synchronizing only extra fields.
    fieldToGetter.remove(name);
    return (B) this;
  }

  /**
   * Adds an {@linkplain ExtraFieldPropagation extra field} into the correlation context.
   *
   * <p>It is the responsibility of the caller to verify the field is a valid extra field. Any
   * incorrect fields will be ignored at runtime.
   *
   * @since 5.11
   */
  public B addExtraField(String name) {
    if (name == null) throw new NullPointerException("name == null");
    if (name.isEmpty()) throw new NullPointerException("name is empty");
    fieldToGetter.put(name, new ExtraFieldGetter(name));
    return (B) this;
  }

  /** @throws IllegalArgumentException if all correlation fields were removed. */
  public final ScopeDecorator build() {
    int fieldCount = fieldToGetter.size();
    if (fieldCount == 0) throw new IllegalArgumentException("no fields");
    if (fieldCount == 1) return new SingleCorrelationFieldScopeDecorator(this);
    return new MultipleCorrelationFieldScopeDecorator(this);
  }

  static abstract class CorrelationFieldScopeDecorator implements ScopeDecorator {
    final Context context;

    CorrelationFieldScopeDecorator(Context context) {
      this.context = context;
    }

    boolean update(@Nullable TraceContext traceContext, Getter getter, String name,
      String previousValue) {
      String currentValue = traceContext != null ? getter.get(traceContext) : null;
      if (currentValue != null) {
        if (!currentValue.equals(previousValue)) {
          context.put(name, currentValue);
          return true;
        }
      } else if (previousValue != null) {
        context.remove(name);
        return true;
      }
      return false;
    }

    final void replace(String name, @Nullable String value) {
      if (value != null) {
        context.put(name, value);
      } else {
        context.remove(name);
      }
    }
  }

  static final class SingleCorrelationFieldScopeDecorator extends CorrelationFieldScopeDecorator {
    final String name;
    final Getter getter;

    SingleCorrelationFieldScopeDecorator(CorrelationFieldScopeDecoratorBuilder<?> builder) {
      super(builder.context);
      Map.Entry<String, Getter> entry = builder.fieldToGetter.entrySet().iterator().next();
      name = entry.getKey();
      getter = entry.getValue();
    }

    @Override public Scope decorateScope(TraceContext traceContext, Scope scope) {
      if (scope == Scope.NOOP) return scope;

      String previousValue = context.get(name);
      if (!update(traceContext, getter, name, previousValue)) {
        return scope;
      }

      class SingleCorrelationFieldScope implements Scope {
        @Override public void close() {
          scope.close();
          replace(name, previousValue);
        }
      }
      return new SingleCorrelationFieldScope();
    }
  }

  static final class MultipleCorrelationFieldScopeDecorator extends CorrelationFieldScopeDecorator {
    final String[] names;
    final Getter[] getters;

    MultipleCorrelationFieldScopeDecorator(CorrelationFieldScopeDecoratorBuilder<?> builder) {
      super(builder.context);
      int fieldCount = builder.fieldToGetter.size();
      names = new String[fieldCount];
      getters = new Getter[fieldCount];
      int i = 0;
      for (Map.Entry<String, Getter> entry : builder.fieldToGetter.entrySet()) {
        names[i] = entry.getKey();
        getters[i++] = entry.getValue();
      }
    }

    @Override public Scope decorateScope(TraceContext traceContext, Scope scope) {
      if (scope == Scope.NOOP) return scope;

      String[] previousValues = new String[names.length];
      boolean changed = false;
      for (int i = 0; i < names.length; i++) {
        String name = names[i];
        Getter getter = getters[i];
        String previousValue = context.get(name);
        if (update(traceContext, getter, name, previousValue)) {
          changed = true;
        }
        previousValues[i] = previousValue;
      }

      if (!changed) return scope;

      class MultipleCorrelationFieldScope implements Scope {
        @Override public void close() {
          scope.close();
          for (int i = 0; i < names.length; i++) {
            replace(names[i], previousValues[i]);
          }
        }
      }
      return new MultipleCorrelationFieldScope();
    }
  }

  interface Getter {
    @Nullable String get(TraceContext context);
  }

  enum TraceContextGetter implements Getter {
    TRACE_ID() {
      @Override public String get(TraceContext context) {
        return context.traceIdString();
      }
    },
    PARENT_ID() {
      @Override public String get(TraceContext context) {
        return context.parentIdString();
      }
    },
    SPAN_ID() {
      @Override public String get(TraceContext context) {
        return context.spanIdString();
      }
    },
    SAMPLED() {
      @Override public String get(TraceContext context) {
        Boolean sampled = context.sampled();
        return sampled != null ? sampled.toString() : null;
      }
    };
  }

  static final class ExtraFieldGetter implements Getter {
    final Class<? extends PropagationFields<String, String>> propagationType =
      InternalPropagation.instance.extraPropagationFieldsType();

    final String name;

    ExtraFieldGetter(String name) {
      this.name = name.toLowerCase(Locale.ROOT); // contract of extra fields internally
    }

    @Override public String get(TraceContext context) {
      return PropagationFields.get(context, name, propagationType);
    }
  }

  protected interface Context {
    /**
     * Returns the correlation property of the specified name iff it is a string, or null
     * otherwise.
     */
    @Nullable String get(String name);

    /** Replaces the correlation property of the specified name */
    void put(String name, String value);

    /** Removes the correlation property of the specified name */
    void remove(String name);
  }
}
