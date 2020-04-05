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

import brave.internal.baggage.BaggageContext;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;

/**
 * Holds {@link CorrelationScopeDecorator} configuration for a {@linkplain BaggageField baggage
 * field}.
 *
 * <h3>Field mapping</h3>
 * Your log correlation properties may not be the same as the baggage field names. You can override
 * them with the builder as needed.
 *
 * <p>Ex. If your log property is %X{trace-id}, you can do this:
 * <pre>{@code
 * scopeBuilder.clear() // TRACE_ID is a default field!
 *             .add(CorrelationField.newBuilder(BaggageFields.TRACE_ID)
 *                                  .name("trace-id").build())
 * }</pre>
 *
 * <h3>Visibility</h3>
 * <p>By default, field updates only apply during {@linkplain CorrelationScopeDecorator scope
 * decoration}. This means values set do not flush immediately to the underlying correlation
 * context. Rather, they are scheduled for the next scope operation as a way to control overhead.
 * {@link CorrelationField#flushOnUpdate()} overrides this.
 *
 * @see CorrelationScopeDecorator
 * @see BaggageField
 * @since 5.11
 */
public final class CorrelationField {
  /** @since 5.11 */
  public static CorrelationField create(BaggageField baggageField) {
    return new Builder(baggageField).build();
  }

  /** @since 5.11 */
  public static Builder newBuilder(BaggageField baggageField) {
    return new Builder(baggageField);
  }

  /**
   * Allows decorators to reconfigure correlation of this {@link #baggageField()}
   *
   * @see CorrelationScopeCustomizer
   * @since 5.11
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  /** @since 5.11 */
  public static final class Builder {
    final BaggageField baggageField;
    String name;
    boolean dirty, flushOnUpdate;

    Builder(BaggageField baggageField) {
      this.baggageField = baggageField;
      this.name = baggageField.name();
    }

    Builder(CorrelationField input) {
      baggageField = input.baggageField;
      name = input.name;
      dirty = input.dirty;
      flushOnUpdate = input.flushOnUpdate;
    }

    /** @see CorrelationField#name() */
    public Builder name(String name) {
      this.name = BaggageField.validateName(name);
      return this;
    }

    /** @see CorrelationField#dirty() */
    public Builder dirty() {
      this.dirty = true;
      return this;
    }

    /** @see CorrelationField#flushOnUpdate() */
    public Builder flushOnUpdate() {
      this.flushOnUpdate = true;
      return this;
    }

    /** @since 5.11 */
    public CorrelationField build() {
      return new CorrelationField(this);
    }
  }

  final BaggageField baggageField;
  final String name;
  final boolean dirty, flushOnUpdate, readOnly;

  CorrelationField(Builder builder) { // sealed to this package
    baggageField = builder.baggageField;
    name = builder.name;
    dirty = builder.dirty;
    flushOnUpdate = builder.flushOnUpdate;
    readOnly = baggageField.context instanceof BaggageContext.ReadOnly;
  }

  public BaggageField baggageField() {
    return baggageField;
  }

  /**
   * The name to use in the correlation context. This defaults to {@link BaggageField#name()} unless
   * overridden by {@link Builder#name(String)}.
   *
   * @since 5.11
   */
  public String name() {
    return name;
  }

  /**
   * Adds a name in the underlying context which is updated directly. The decorator will overwrite
   * any underlying changes when the scope closes.
   *
   * <p>This is used when there are a mix of libraries controlling the same correlation field.
   * For example, if SLF4J MDC can update the same field name.
   *
   * <p>This has a similar performance impact to {@link #flushOnUpdate()}, as it requires tracking
   * the field value even if there's no change detected.
   *
   * @since 5.11
   */
  public boolean dirty() {
    return dirty;
  }

  /**
   * When true, updates made to this name via {@linkplain BaggageField#updateValue(TraceContext,
   * String)} flush immediately to the correlation context.
   *
   * <p>This is useful for callbacks that have a void return. Ex.
   * <pre>{@code
   * @SendTo(SourceChannels.OUTPUT)
   * public void timerMessageSource() {
   *   // Assume BUSINESS_PROCESS is an updatable field
   *   BUSINESS_PROCESS.updateValue("accounting");
   *   // Assuming a Log4j context, the expression %{bp} will show "accounting" in businessCode()
   *   businessCode();
   * }
   * }</pre>
   *
   * <h3>Appropriate Usage</h3>
   * This has a significant performance impact as it requires even {@link
   * CurrentTraceContext#maybeScope(TraceContext)} to always track values.
   *
   * <p>Most fields do not change in the scope of a {@link TraceContext}. For example, standard
   * fields such as {@link BaggageFields#SPAN_ID the span ID} and {@linkplain
   * BaggageFields#constant(String, String) constants} such as env variables do not need to be
   * tracked. Even field value updates do not necessarily need to be flushed to the underlying
   * correlation context, as they will apply on the next scope operation.
   *
   * @since 5.11
   */
  public boolean flushOnUpdate() {
    return flushOnUpdate;
  }

  /** Returns true if this value is immutable within a {@link TraceContext}. */
  public boolean readOnly() {
    return readOnly;
  }

  @Override public String toString() {
    String baggageName = baggageField.name;
    if (baggageName.equals(name)) {
      return "CorrelationField{" + name + "}";
    }
    return "CorrelationField{" + baggageName + "->" + name + "}";
  }

  /** Returns true for any correlation field with the same baggage field. */
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof CorrelationField)) return false;
    return baggageField.equals(((CorrelationField) o).baggageField);
  }

  /** Returns the same value for any correlation field with the same baggage field. */
  @Override public int hashCode() {
    return baggageField.hashCode();
  }
}
