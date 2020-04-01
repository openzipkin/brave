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
package brave.propagation;

import brave.NoopSpanCustomizer;
import brave.ScopedSpan;
import brave.Span;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.handler.MutableSpan;
import brave.internal.Nullable;
import java.util.Locale;

/**
 * A possibly remote field used for correlation such as MDC via {@link CorrelationScopeDecorator}.
 *
 * Here's an example of a constant field, which you want to add a span tag.
 * <pre>{@code
 * CLOUD_REGION = CorrelationFields.constant("region", System.getEnv("CLOUD_REGION"));
 *
 * // Later, you can call below to add a tag with the field name to a span.
 * CLOUD_REGION.tag(span);
 * }</pre>
 *
 * <h3>Updatable fields</h3>
 * {@link Updatable} fields are mutable, typically in request scope. The most common mutable field
 * is {@linkplain BaggageField baggage}.
 *
 * <p>Ex. If you have access to an updatable field, you can reset its value at runtime.
 * <pre>{@code
 * // Later, you can call below to affect the country code of the current trace context
 * COUNTRY_CODE.updateValue("FO");
 * String countryCode = COUNTRY_CODE.getValue();
 * COUNTRY_CODE.tag(span);
 * }</pre>
 *
 * @see CorrelationFields
 * @see CorrelationScopeDecorator
 * @see BaggageField
 * @see BaggagePropagation
 * @since 5.11
 */
public abstract class CorrelationField {
  /**
   * The non-empty name of the field. Ex "userId".
   *
   * <p>For example, if using log correlation and with field named "userId", the {@linkplain
   * #getValue(TraceContext) value} becomes the log variable {@code %{userId}} when the span is next
   * made current.
   *
   * @see CorrelationScopeDecorator
   */
  public final String name() {
    return name;
  }

  /**
   * Returns the most recent value of the field named {@link #name()} in the context or null if
   * unavailable.
   */
  @Nullable public abstract String getValue(TraceContext context);

  /**
   * Like {@link #getValue(TraceContext)} except against the current trace context.
   *
   * <p>Prefer {@link #getValue(TraceContext)} if you have a reference to the trace context.
   */
  @Nullable public String getValue() {
    TraceContext context = currentTraceContext();
    return context != null ? getValue(context) : null;
  }

  /**
   * {@linkplain ScopedSpan#tag(String, String) tags} the value if the input is not no-op and field
   * value is available.
   */
  public final void tag(ScopedSpan scopedSpan) {
    if (scopedSpan.isNoop()) return;
    String value = getValue(scopedSpan.context());
    if (value != null) scopedSpan.tag(name, value);
  }

  /**
   * {@linkplain Span#tag(String, String) tags} the value if the input is not no-op and field value
   * is available.
   */
  public final void tag(Span span) {
    if (span.isNoop()) return;
    String value = getValue(span.context());
    if (value != null) span.tag(name, value);
  }

  /**
   * {@linkplain SpanCustomizer#tag(String, String) tags} the value if the input is not no-op and
   * field value is available.
   */
  public final void tag(TraceContext context, SpanCustomizer customizer) {
    if (customizer instanceof Span) {
      tag((Span) customizer);
      return;
    }
    if (customizer == NoopSpanCustomizer.INSTANCE) return;
    String value = getValue(context);
    if (value != null) customizer.tag(name, value);
  }

  /**
   * {@linkplain MutableSpan#tag(String, String) tags} the value if the input is not no-op and field
   * value is available.
   */
  public final void tag(TraceContext context, MutableSpan span) {
    String value = getValue(context);
    if (value != null) span.tag(name, value);
  }

  public static abstract class Updatable extends CorrelationField {
    /** Updates the value of the this field, or ignores if not configured. */
    public abstract void updateValue(TraceContext context, @Nullable String value);

    /**
     * Like {@link #updateValue(TraceContext, String)} except against the current trace context.
     *
     * <p>Prefer {@link #updateValue(TraceContext, String)} if you have a reference to the trace
     * context.
     */
    public void updateValue(String value) {
      TraceContext context = currentTraceContext();
      if (context != null) updateValue(context, value);
    }

    /**
     * When true, updates made via {@linkplain #updateValue(TraceContext, String)} flush immediately
     * to the correlation context.
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
     * fields such as {@link CorrelationFields#SPAN_ID the span ID} and {@linkplain
     * CorrelationFields#constant(String, String) constants} such as env variables do not need to be
     * tracked. Even field value updates do not necessarily need to be flushed to the underlying
     * correlation context, as they will apply on the next scope operation.
     */
    public final boolean flushOnUpdate() {
      return flushOnUpdate;
    }

    final boolean flushOnUpdate;

    Updatable(String name, boolean flushOnUpdate) { // package sealed
      super(name);
      this.flushOnUpdate = flushOnUpdate;
    }
  }

  final String name, lcName;

  CorrelationField(String name) { // sealed to this package
    this.name = validateName(name);
    this.lcName = name.toLowerCase(Locale.ROOT);
  }

  @Override public String toString() {
    return getClass().getSimpleName() + "{" + name + "}";
  }

  /** Returns true for any correlation field with the same name (case insensitive). */
  @Override public final boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof CorrelationField)) return false;
    return lcName.equals(((CorrelationField) o).lcName);
  }

  /** Returns the same value for any correlation field with the same name (case insensitive). */
  @Override public final int hashCode() {
    return lcName.hashCode();
  }

  static String validateName(String name) {
    if (name == null) throw new NullPointerException("name == null");
    name = name.trim();
    if (name.isEmpty()) throw new IllegalArgumentException("name is empty");
    return name;
  }

  @Nullable static TraceContext currentTraceContext() {
    Tracing tracing = Tracing.current();
    return tracing != null ? tracing.currentTraceContext().get() : null;
  }
}
