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

import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Synchronizes fields such as {@link BaggageFields#TRACE_ID} with a correlation context, such as
 * logging through decoration of a scope. A maximum of 32 fields are supported.
 *
 * <p>Setup example:
 * <pre>{@code
 * import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
 *
 * // Add the field "region", so it can be used as a log expression %X{region}
 * CLOUD_REGION = BaggageFields.constant("region", System.getEnv("CLOUD_REGION"));
 *
 * decorator = MDCScopeDecorator.newBuilder()
 *                              .add(SingleCorrelationField.create(CLOUD_REGION))
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
 * @see CorrelationScopeConfig
 * @see CorrelationScopeCustomizer
 * @see BaggagePropagation
 * @since 5.11
 */
public abstract class CorrelationScopeDecorator implements ScopeDecorator {
  /** Defaults to {@link BaggageFields#TRACE_ID} and {@link BaggageFields#SPAN_ID}. */
  // do not define newBuilder or create() here as it will mask subtypes
  public static abstract class Builder {
    final CorrelationContext context;
    // Don't allow mixed case of the same name!
    final Set<String> allNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    final Set<SingleCorrelationField> fields = new LinkedHashSet<>();

    /** Internal constructor used by subtypes. */
    protected Builder(CorrelationContext context) {
      if (context == null) throw new NullPointerException("context == null");
      this.context = context;
      add(SingleCorrelationField.create(BaggageFields.TRACE_ID));
      add(SingleCorrelationField.create(BaggageFields.SPAN_ID));
    }

    /**
     * Returns an immutable copy of the current {@linkplain #add(CorrelationScopeConfig)
     * configuration}. This allows those who can't create the builder to reconfigure this builder.
     *
     * @see #clear()
     * @since 5.11
     */
    public Set<CorrelationScopeConfig> configs() {
      return Collections.unmodifiableSet(new LinkedHashSet<>(fields));
    }

    /**
     * Invoke this to clear fields so that you can {@linkplain #add(CorrelationScopeConfig) add the
     * ones you need}.
     *
     * <p>Defaults may include a field you aren't using, such as {@link BaggageFields#PARENT_ID}.
     * For best performance, only include the fields you use in your correlation expressions (such
     * as log formats).
     *
     * @see #configs()
     * @see CorrelationScopeDecorator
     * @since 5.11
     */
    public Builder clear() {
      allNames.clear();
      fields.clear();
      return this;
    }

    /** @since 5.11 */
    public Builder add(CorrelationScopeConfig config) {
      if (config == null) throw new NullPointerException("config == null");
      if (!(config instanceof SingleCorrelationField)) {
        throw new UnsupportedOperationException("dynamic fields not yet supported");
      }
      SingleCorrelationField field = (SingleCorrelationField) config;
      if (fields.contains(field)) {
        throw new IllegalArgumentException(
          "Baggage Field already added: " + field.baggageField.name);
      }
      if (allNames.contains(field.name)) {
        throw new IllegalArgumentException("Correlation name already in use: " + field.name);
      }
      fields.add(field);
      return this;
    }

    /** @return {@link ScopeDecorator#NOOP} if no baggage fields were added. */
    public final ScopeDecorator build() {
      int fieldCount = fields.size();
      if (fieldCount == 0) return ScopeDecorator.NOOP;
      if (fieldCount == 1) return new Single(context, fields.iterator().next());
      if (fieldCount > 32) throw new IllegalArgumentException("over 32 baggage fields");
      return new Multiple(context, fields.toArray(new SingleCorrelationField[0]));
    }
  }

  final CorrelationContext context;

  CorrelationScopeDecorator(CorrelationContext context) {
    this.context = context;
  }

  static final class Single extends CorrelationScopeDecorator {
    final SingleCorrelationField field;

    Single(CorrelationContext context, SingleCorrelationField field) {
      super(context);
      this.field = field;
    }

    @Override public Scope decorateScope(@Nullable TraceContext traceContext, Scope scope) {
      String valueToRevert = context.getValue(field.name);
      String currentValue = field.baggageField.getValue(traceContext);

      boolean dirty = false;
      if (scope != Scope.NOOP || !field.readOnly()) {
        dirty = !equal(valueToRevert, currentValue);
        if (dirty) context.update(field.name, currentValue);
      }

      // If the underlying field might be updated, always revert the value
      dirty = dirty || field.dirty;

      if (!dirty && !field.flushOnUpdate) return scope;

      // If there was or could be a value update, we need to track values to revert.
      CorrelationUpdateScope updateScope =
        new CorrelationUpdateScope.Single(scope, context, field, valueToRevert, dirty);
      return field.flushOnUpdate ? new CorrelationFlushScope(updateScope) : updateScope;
    }
  }

  static final class Multiple extends CorrelationScopeDecorator {
    final SingleCorrelationField[] fields;

    Multiple(CorrelationContext context, SingleCorrelationField[] fields) {
      super(context);
      this.fields = fields;
    }

    @Override public Scope decorateScope(@Nullable TraceContext traceContext, Scope scope) {
      int dirty = 0;
      boolean flushOnUpdate = false;

      String[] valuesToRevert = new String[fields.length];
      for (int i = 0; i < fields.length; i++) {
        SingleCorrelationField field = fields[i];
        String valueToRevert = context.getValue(field.name);
        String currentValue = field.baggageField.getValue(traceContext);

        if (scope != Scope.NOOP || !field.readOnly) {
          if (!equal(valueToRevert, currentValue)) {
            context.update(field.name, currentValue);
            dirty = setBit(dirty, i);
          }
        }

        // Always revert fields that could be updated in the context directly
        if (field.dirty) dirty = setBit(dirty, i);
        if (field.flushOnUpdate) flushOnUpdate = true;

        valuesToRevert[i] = valueToRevert;
      }

      if (dirty == 0 && !flushOnUpdate) return scope;

      // If there was or could be a value update, we need to track values to revert.
      CorrelationUpdateScope updateScope =
        new CorrelationUpdateScope.Multiple(scope, context, fields, valuesToRevert, dirty);
      return flushOnUpdate ? new CorrelationFlushScope(updateScope) : updateScope;
    }
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
