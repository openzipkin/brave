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
package brave.baggage;

import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Synchronizes fields such as {@link BaggageFields#TRACE_ID} with a correlation context, such as
 * logging through decoration of a scope. A maximum of 32 fields are supported.
 *
 * <p>Setup example:
 * <pre>{@code
 * // Add the field "region", so it can be used as a log expression %X{region}
 * CLOUD_REGION = BaggageFields.constant("region", System.getEnv("CLOUD_REGION"));
 * decorator = MDCScopeDecorator.newBuilder()
 *                              .addField(CLOUD_REGION)
 *                              .build();
 *
 * // Integrate the decorator
 * tracing = Tracing.newBuilder()
 *                  .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                    .addScopeDecorator(decorator)
 *                    .build())
 *                  ...
 *                  .build();
 *
 * // Any scope operations (updates to the current span) apply the fields defined by the decorator.
 * ScopedSpan span = tracing.tracer().startScopedSpan("encode");
 * try {
 *   // The below log message will have %X{region} in the context!
 *   logger.info("Encoding the span, hope it works");
 *   return encoder.encode();
 * } catch (RuntimeException | Error e) {
 *   span.error(e); // Unless you handle exceptions, you might not know the operation failed!
 *   throw e;
 * } finally {
 *   span.finish();
 * }
 * }</pre>
 *
 * <h3>Visibility</h3>
 * <p>By default, field updates only apply during {@linkplain CorrelationScopeDecorator scope
 * decoration}. This means values set do not flush immediately to the underlying correlation
 * context. Rather, they are scheduled for the next scope operation as a way to control overhead.
 * {@link BaggageField#flushOnUpdate()} overrides this.
 *
 * @see BaggageField
 * @since 5.11
 */
public abstract class CorrelationScopeDecorator implements ScopeDecorator {
  /** Defaults to {@link BaggageFields#TRACE_ID} and {@link BaggageFields#SPAN_ID}. */
  // do not define newBuilder or create() here as it will mask subtypes
  public static abstract class Builder {
    final CorrelationContext context;
    final Set<BaggageField> fields = new LinkedHashSet<>();

    /** Internal constructor used by subtypes. */
    protected Builder(CorrelationContext context) {
      if (context == null) throw new NullPointerException("context == null");
      this.context = context;
      fields.add(BaggageFields.TRACE_ID);
      fields.add(BaggageFields.SPAN_ID);
    }

    /**
     * Invoke this to clear fields so that you can {@linkplain #addField(BaggageField) add the ones
     * you need}.
     *
     * <p>Defaults may include a field you aren't using, such as "parentId". For best
     * performance, only include the fields you use in your correlation expressions (such as log
     * formats).
     *
     * @since 5.11
     */
    public Builder clearFields() {
      this.fields.clear();
      return this;
    }

    /**
     * @see BaggagePropagation.FactoryBuilder#addField(BaggageField)
     * @since 5.11
     */
    public Builder addField(BaggageField field) {
      if (field == null) throw new NullPointerException("field == null");
      if (field.name() == null) throw new NullPointerException("field.name() == null");
      if (field.name().isEmpty()) throw new NullPointerException("field.name() isEmpty");
      fields.add(field);
      return this;
    }

    /** @throws IllegalArgumentException if no baggage fields were added. */
    public final CorrelationScopeDecorator build() {
      int fieldCount = fields.size();
      if (fieldCount == 0) throw new IllegalArgumentException("no baggage fields");
      if (fieldCount == 1) return new Single(context, fields.iterator().next());
      if (fieldCount > 32) throw new IllegalArgumentException("over 32 baggage fields");
      return new Multiple(context, fields);
    }
  }

  final CorrelationContext context;

  CorrelationScopeDecorator(CorrelationContext context) {
    this.context = context;
  }

  static final class Single extends CorrelationScopeDecorator {
    final BaggageField field;

    Single(CorrelationContext context, BaggageField field) {
      super(context);
      this.field = field;
    }

    @Override public Scope decorateScope(@Nullable TraceContext traceContext, Scope scope) {
      String valueToRevert = context.getValue(field.name());
      String currentValue = traceContext != null ? field.getValue(traceContext) : null;

      boolean dirty = false;
      if (scope != Scope.NOOP || !readOnly(field)) {
        dirty = !equal(valueToRevert, currentValue);
        if (dirty) context.update(field.name, currentValue);
      }

      if (!dirty && !field.flushOnUpdate()) return scope;

      // If there was or could be a value update, we need to track values to revert.
      BaggageFieldUpdateScope updateScope =
        new BaggageFieldUpdateScope.Single(scope, context, field, valueToRevert, dirty);
      return field.flushOnUpdate() ? new BaggageFieldFlushScope(updateScope) : updateScope;
    }
  }

  static final class Multiple extends CorrelationScopeDecorator {
    final BaggageField[] fields;

    Multiple(CorrelationContext context, Set<BaggageField> baggageFields) {
      super(context);
      fields = baggageFields.toArray(new BaggageField[0]);
    }

    @Override public Scope decorateScope(@Nullable TraceContext traceContext, Scope scope) {
      int dirty = 0, flushOnUpdate = 0;
      String[] valuesToRevert = new String[fields.length];
      for (int i = 0; i < fields.length; i++) {
        BaggageField field = fields[i];
        String valueToRevert = context.getValue(field.name());
        String currentValue = traceContext != null ? field.getValue(traceContext) : null;

        if (scope != Scope.NOOP || !readOnly(field)) {
          if (!equal(valueToRevert, currentValue)) {
            context.update(field.name, currentValue);
            dirty = setBit(dirty, i);
          }
        }

        if (field.flushOnUpdate()) {
          flushOnUpdate = setBit(flushOnUpdate, i);
        }

        valuesToRevert[i] = valueToRevert;
      }

      if (dirty == 0 && flushOnUpdate == 0) return scope;

      // If there was or could be a value update, we need to track values to revert.
      BaggageFieldUpdateScope updateScope =
        new BaggageFieldUpdateScope.Multiple(scope, context, fields, valuesToRevert, dirty);
      return flushOnUpdate != 0 ? new BaggageFieldFlushScope(updateScope) : updateScope;
    }
  }

  static boolean readOnly(BaggageField field) {
    return field.context instanceof BaggageContext.ReadOnly;
  }

  static int setBit(int bitset, int i) {
    return bitset | (1 << i);
  }

  static boolean isSet(int bitset, int i) {
    return (bitset & (1 << i)) != 0;
  }

  static boolean equal(@Nullable Object a, @Nullable Object b) {
    return a == null ? b == null : a.equals(b); // Java 6 can't use Objects.equals()
  }
}
