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
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.TraceContext;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Internal type that adds correlation properties "traceId", "parentId", "spanId" and "sampled" when
 * a {@linkplain Tracer#currentSpan() span is current}. "traceId" and "spanId" are used in log
 * correlation. "parentId" is used for scenarios such as log parsing that reconstructs the trace
 * tree. "sampled" is used as a hint that a span found in logs might be in Zipkin.
 */
public abstract class CorrelationFieldScopeDecorator implements ScopeDecorator {
  final Updater[] updaters;

  protected static abstract class Builder<B extends Builder<B>> {
    final Set<String> extraFields = new LinkedHashSet<>(); // insertion order;

    /**
     * Adds a field from {@link ExtraFieldPropagation} into the correlation context.
     *
     * @since 5.11
     */
    public B addExtraField(String name) {
      if (name == null) throw new NullPointerException("name == null");
      String lowercase = name.toLowerCase(Locale.ROOT); // contract of extra fields internally
      extraFields.add(lowercase);
      return (B) this;
    }

    public abstract CurrentTraceContext.ScopeDecorator build();
  }

  protected CorrelationFieldScopeDecorator(Builder<?> builder) {
    String[] extraFields = builder.extraFields.toArray(new String[0]);
    updaters = new Updater[4 + extraFields.length];
    updaters[0] = new TraceIdUpdater(this);
    updaters[1] = new ParentSpanIdUpdater(this);
    updaters[2] = new SpanIdUpdater(this);
    updaters[3] = new SampledUpdater(this);
    for (int i = 0; i < extraFields.length; i++) {
      updaters[4 + i] = new ExtraFieldUpdater(this, extraFields[i]);
    }
  }

  /**
   * When the input is not null "traceId", "parentId", "spanId" and "sampled" correlation properties
   * are saved off and replaced with those of the current span. When the input is null, these
   * properties are removed. Either way, "traceId", "parentId", "spanId" and "sampled" properties
   * are restored on {@linkplain Scope#close()}.
   */
  @Override public Scope decorateScope(@Nullable TraceContext context, Scope scope) {
    String[] previousValues = new String[updaters.length];

    boolean changed = false;
    for (int i = 0; i < updaters.length; i++) {
      previousValues[i] = get(updaters[i].field);
      if (context != null) {
        if (updaters[i].update(context, previousValues[i])) {
          changed = true;
        }
      } else if (previousValues[i] != null) {
        remove(updaters[i].field);
        changed = true;
      }
    }

    if (!changed) return scope;

    class CorrelationFieldCurrentTraceContextScope implements Scope {
      @Override public void close() {
        scope.close();
        for (int i = 0; i < updaters.length; i++) {
          replace(updaters[i].field, previousValues[i]);
        }
      }
    }
    return new CorrelationFieldCurrentTraceContextScope();
  }

  static final class ExtraFieldUpdater extends Updater {
    final Class<? extends PropagationFields<String, String>> propagationType;

    ExtraFieldUpdater(CorrelationFieldScopeDecorator decorator, String name) {
      super(decorator, name);
      this.propagationType = InternalPropagation.instance.extraPropagationFieldsType();
    }

    @Override boolean update(TraceContext context, @Nullable String previous) {
      String current = PropagationFields.get(context, field, propagationType);
      return updateNullable(previous, current);
    }
  }

  static final class TraceIdUpdater extends Updater {
    TraceIdUpdater(CorrelationFieldScopeDecorator decorator) {
      super(decorator, "traceId");
    }

    @Override boolean update(TraceContext context, @Nullable String previous) {
      return update(previous, context.traceIdString());
    }
  }

  static final class ParentSpanIdUpdater extends Updater {
    ParentSpanIdUpdater(CorrelationFieldScopeDecorator decorator) {
      super(decorator, "parentId");
    }

    @Override boolean update(TraceContext context, @Nullable String previous) {
      return updateNullable(previous, context.parentIdString());
    }
  }

  static final class SpanIdUpdater extends Updater {
    SpanIdUpdater(CorrelationFieldScopeDecorator decorator) {
      super(decorator, "spanId");
    }

    @Override boolean update(TraceContext context, @Nullable String previous) {
      return update(previous, context.spanIdString());
    }
  }

  static final class SampledUpdater extends Updater {
    SampledUpdater(CorrelationFieldScopeDecorator decorator) {
      super(decorator, "sampled");
    }

    @Override boolean update(TraceContext context, @Nullable String previous) {
      Boolean sampled = context.sampled();
      return updateNullable(previous, sampled != null ? sampled.toString() : null);
    }
  }

  static abstract class Updater {
    final CorrelationFieldScopeDecorator decorator;
    final String field;

    Updater(CorrelationFieldScopeDecorator decorator, String field) {
      this.decorator = decorator;
      this.field = field;
    }

    /** Returns true if there was a change to the correlation field. */
    abstract boolean update(TraceContext context, @Nullable String previous);

    boolean update(@Nullable String previous, String current) {
      if (!current.equals(previous)) {
        decorator.put(field, current);
        return true;
      }
      return false;
    }

    boolean updateNullable(@Nullable String previous, @Nullable String current) {
      if (current != null) {
        return update(previous, current);
      } else if (previous != null) {
        decorator.remove(field);
        return true;
      } else {
        return false;
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

  final void replace(String key, @Nullable String value) {
    if (value != null) {
      put(key, value);
    } else {
      remove(key);
    }
  }
}
