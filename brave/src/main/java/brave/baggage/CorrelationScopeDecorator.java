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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import static brave.baggage.BaggageField.validateName;

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
 * <h3>Field mapping</h3>
 * Your log correlation properties may not be the same as the baggage field names. You can override
 * them in the builder as needed.
 *
 * <p>Ex. If your log property is %X{trace-id}, you can do this:
 * <pre>{@code
 * builder.clear(); // traceId is a default field!
 * builder.addField(BaggageFields.TRACE_ID, "trace-id");
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
    // Don't allow mixed case of the same name!
    final Set<String> allNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    final Set<String> dirtyNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    final Map<BaggageField, String> fieldToNames = new LinkedHashMap<>();

    /** Internal constructor used by subtypes. */
    protected Builder(CorrelationContext context) {
      if (context == null) throw new NullPointerException("context == null");
      this.context = context;
      addField(BaggageFields.TRACE_ID);
      addField(BaggageFields.SPAN_ID);
    }

    /**
     * Returns an immutable copy of the currently configured {@linkplain #addDirtyName(String) dirty
     * names}. This allows those who can't create the builder to reconfigure this builder.
     *
     * @see #clear()
     * @since 5.11
     */
    public Set<String> dirtyNames() {
      return Collections.unmodifiableSet(new LinkedHashSet<>(dirtyNames));
    }

    /**
     * Returns an immutable copy of the currently configured fields mapped to names for use in
     * correlation. This allows those who can't create the builder to reconfigure this builder.
     *
     * @see #clear()
     * @since 5.11
     */
    public Map<BaggageField, String> fieldToNames() {
      return Collections.unmodifiableMap(new LinkedHashMap<>(fieldToNames));
    }

    /**
     * Invoke this to clear fields so that you can {@linkplain #addField(BaggageField) add the ones
     * you need}.
     *
     * <p>Defaults may include a field you aren't using, such as {@link BaggageFields#PARENT_ID}.
     * For best performance, only include the fields you use in your correlation expressions (such
     * as log formats).
     *
     * @see #fieldToNames()
     * @see CorrelationScopeDecorator
     * @since 5.11
     */
    public Builder clear() {
      allNames.clear();
      dirtyNames.clear();
      fieldToNames.clear();
      return this;
    }

    /**
     * Adds a correlation property into the context with its {@link BaggageField#name()}.
     *
     * @since 5.11
     */
    public Builder addField(BaggageField field) {
      return addField(field, field.name);
    }

    /**
     * Adds a correlation property into the context with an alternate name.
     *
     * <p>For example, if your log correlation needs the name "trace-id", you can do this:
     * <pre>{@code
     * builder.clear(); // traceId is a default field!
     * builder.addField(BaggageFields.TRACE_ID, "trace-id");
     * }</pre>
     *
     * @since 5.11
     */
    public Builder addField(BaggageField field, String name) {
      if (field == null) throw new NullPointerException("field == null");
      if (fieldToNames.containsKey(field)) {
        throw new IllegalArgumentException("Field already added: " + field.name);
      }

      name = validateName(name);
      if (allNames.contains(name)) {
        throw new IllegalArgumentException("Correlation name already in use: " + name);
      }
      allNames.add(name);
      fieldToNames.put(field, name);
      return this;
    }

    /**
     * Adds a name in the underlying context which is updated directly. The decorator will overwrite
     * any underlying changes when the scope closes.
     *
     * <p>This is used when there are a mix of libraries controlling the same correlation field.
     * For example, if SLF4J MDC can update the same field name.
     *
     * <p>This has a similar performance impact to {@link BaggageField#flushOnUpdate()}, as it
     * requires tracking the field value even if there's no change detected.
     *
     * @since 5.11
     */
    public Builder addDirtyName(String name) {
      name = validateName(name);
      if (!allNames.contains(name)) {
        throw new IllegalArgumentException("Correlation name not in use: " + name);
      }
      dirtyNames.add(name);
      return this;
    }

    /** @return {@link ScopeDecorator#NOOP} if no baggage fields were added. */
    public final ScopeDecorator build() {
      int fieldCount = fieldToNames.size();
      if (fieldCount == 0) return ScopeDecorator.NOOP;
      if (fieldCount == 1) {
        Entry<BaggageField, String> onlyEntry = fieldToNames.entrySet().iterator().next();
        return new Single(context, onlyEntry.getKey(), onlyEntry.getValue(), dirtyNames.size() > 0);
      }
      if (fieldCount > 32) throw new IllegalArgumentException("over 32 baggage fields");
      BaggageField[] fields = new BaggageField[fieldCount];
      String[] names = new String[fieldCount];
      int dirty = 0;
      int i = 0;
      for (Entry<BaggageField, String> next : fieldToNames.entrySet()) {
        fields[i] = next.getKey();
        names[i] = next.getValue();
        if (dirtyNames.contains(next.getValue())) dirty = setBit(dirty, i);
        i++;
      }
      return new Multiple(context, fields, names, dirty);
    }
  }

  final CorrelationContext context;

  CorrelationScopeDecorator(CorrelationContext context) {
    this.context = context;
  }

  static final class Single extends CorrelationScopeDecorator {
    final BaggageField field;
    final String name;
    final boolean dirtyName;

    Single(CorrelationContext context, BaggageField field, String name, boolean dirtyName) {
      super(context);
      this.field = field;
      this.name = name;
      this.dirtyName = dirtyName;
    }

    @Override public Scope decorateScope(@Nullable TraceContext traceContext, Scope scope) {
      String valueToRevert = context.getValue(name);
      String currentValue = traceContext != null ? field.getValue(traceContext) : null;

      boolean dirty = false;
      if (scope != Scope.NOOP || !readOnly(field)) {
        dirty = !equal(valueToRevert, currentValue);
        if (dirty) context.update(name, currentValue);
      }

      // If the underlying field might be updated, always revert the value
      if (!dirty && dirtyName) dirty = true;

      if (!dirty && !field.flushOnUpdate()) return scope;

      // If there was or could be a value update, we need to track values to revert.
      CorrelationUpdateScope updateScope =
        new CorrelationUpdateScope.Single(scope, context, field, name, valueToRevert, dirty);
      return field.flushOnUpdate() ? new CorrrelationFlushScope(updateScope) : updateScope;
    }
  }

  static final class Multiple extends CorrelationScopeDecorator {
    final BaggageField[] fields;
    final String[] names;
    final int dirtyNames;

    Multiple(CorrelationContext context, BaggageField[] fields, String[] names, int dirtyNames) {
      super(context);
      this.fields = fields;
      this.names = names;
      this.dirtyNames = dirtyNames;
    }

    @Override public Scope decorateScope(@Nullable TraceContext traceContext, Scope scope) {
      int dirty = 0, flushOnUpdate = 0;
      String[] valuesToRevert = new String[fields.length];
      for (int i = 0; i < fields.length; i++) {
        BaggageField field = fields[i];
        String valueToRevert = context.getValue(names[i]);
        String currentValue = traceContext != null ? field.getValue(traceContext) : null;

        if (scope != Scope.NOOP || !readOnly(field)) {
          if (!equal(valueToRevert, currentValue)) {
            context.update(names[i], currentValue);
            dirty = setBit(dirty, i);
          }
        }

        if (field.flushOnUpdate()) {
          flushOnUpdate = setBit(flushOnUpdate, i);
        }

        valuesToRevert[i] = valueToRevert;
      }

      // Always revert fields that could be updated in the context directly
      dirty |= dirtyNames;

      if (dirty == 0 && flushOnUpdate == 0) return scope;

      // If there was or could be a value update, we need to track values to revert.
      CorrelationUpdateScope updateScope =
        new CorrelationUpdateScope.Multiple(scope, context, fields, names, valuesToRevert, dirty);
      return flushOnUpdate != 0 ? new CorrrelationFlushScope(updateScope) : updateScope;
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
