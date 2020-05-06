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

import brave.Tracing;
import brave.internal.InternalBaggage;
import brave.internal.Nullable;
import brave.internal.baggage.BaggageCodec;
import brave.internal.baggage.BaggageContext;
import brave.internal.baggage.ExtraBaggageContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Defines a trace context scoped field, usually but not always analogous to an HTTP header. Fields
 * will be no-op unless {@link BaggagePropagation} is configured.
 *
 * <p>For example, if you have a need to know a specific request's country code in a downstream
 * service, you can propagate it through the trace:
 * <pre>{@code
 * // Configure your baggage field
 * COUNTRY_CODE = BaggageField.create("country-code");
 * }</pre>
 *
 * <h3>Usage</h3>
 * As long as a field is configured with {@link BaggagePropagation}, local reads and updates are
 * possible in-process.
 *
 * <p>Ex. once added to `BaggagePropagation`, you can call below to affect the country code
 * of the current trace context:
 * <pre>{@code
 * COUNTRY_CODE.updateValue("FO");
 * String countryCode = COUNTRY_CODE.get();
 * }</pre>
 *
 * <p>Or, if you have a reference to a trace context, it is more efficient to use it explicitly:
 * <pre>{@code
 * COUNTRY_CODE.updateValue(span.context(), "FO");
 * String countryCode = COUNTRY_CODE.get(span.context());
 * Tags.BAGGAGE_FIELD.tag(COUNTRY_CODE, span);
 * }</pre>
 *
 * <p>Correlation</p>
 *
 * <p>You can also integrate baggage with other correlated contexts such as logging:
 * <pre>{@code
 * import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
 * import brave.baggage.CorrelationScopeConfig.SingleCorrelationField;
 *
 * AMZN_TRACE_ID = BaggageField.create("x-amzn-trace-id");
 *
 * // Allow logging patterns like %X{traceId} %X{x-amzn-trace-id}
 * decorator = MDCScopeDecorator.newBuilder()
 *                              .add(SingleCorrelationField.create(AMZN_TRACE_ID)).build()
 *
 * tracingBuilder.propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                                                     .add(SingleBaggageField.remote(AMZN_TRACE_ID))
 *                                                     .build())
 *               .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                                                                  .addScopeDecorator(decorator)
 *                                                                  .build())
 * }</pre>
 *
 * <h3>Appropriate usage</h3>
 * It is generally not a good idea to use the tracing system for application logic or critical code
 * such as security context propagation.
 *
 * <p>Brave is an infrastructure library: you will create lock-in if you expose its apis into
 * business code. Prefer exposing your own types for utility functions that use this class as this
 * will insulate you from lock-in.
 *
 * <p>While it may seem convenient, do not use this for security context propagation as it was not
 * designed for this use case. For example, anything placed in here can be accessed by any code in
 * the same classloader!
 *
 * <h3>Background</h3>
 * The name Baggage was first introduced by Brown University in <a href="https://people.mpi-sws.org/~jcmace/papers/mace2015pivot.pdf">Pivot
 * Tracing</a> as maps, sets and tuples. They then spun baggage out as a standalone component, <a
 * href="https://people.mpi-sws.org/~jcmace/papers/mace2018universal.pdf">BaggageContext</a> and
 * considered some of the nuances of making it general purpose. The implementations proposed in
 * these papers are different to the implementation here, but conceptually the goal is the same: to
 * propagate "arbitrary stuff" with a request.
 *
 * @see BaggagePropagation
 * @see CorrelationScopeConfig
 * @since 5.11
 */
public final class BaggageField {
  /**
   * Used to decouple baggage value updates from {@link TraceContext} or {@link
   * TraceContextOrSamplingFlags} storage.
   *
   * <p><em>Note</em>: This type is safe to implement as a lambda, or use as a method reference as
   * it is effectively a {@code FunctionalInterface}. It isn't annotated as such because the project
   * has a minimum Java language level 6.
   *
   * @since 5.12
   */
  // @FunctionalInterface, except Java language level 6. Do not add methods as it will break API!
  public interface ValueUpdater {
    /** @since 5.12 */
    ValueUpdater NOOP = new ValueUpdater() {
      @Override public boolean updateValue(BaggageField field, String value) {
        return false;
      }

      @Override public String toString() {
        return "NoopValueUpdater{}";
      }
    };

    /**
     * Updates the value of the field, or ignores if read-only or not configured.
     *
     * @param value {@code null} is an attempt to remove the value
     * @return {@code true} if the underlying state changed
     * @see #updateValue(TraceContext, String)
     * @see #updateValue(TraceContextOrSamplingFlags, String)
     * @since 5.12
     */
    boolean updateValue(BaggageField field, @Nullable String value);
  }

  /**
   * @param name See {@link #name()}
   * @since 5.11
   */
  public static BaggageField create(String name) {
    return new BaggageField(name, ExtraBaggageContext.get());
  }

  /** @deprecated Since 5.12 use {@link #getAllValues(TraceContext)} */
  @Deprecated public static List<BaggageField> getAll(@Nullable TraceContext context) {
    if (context == null) return Collections.emptyList();
    return ExtraBaggageContext.getAllFields(context);
  }

  /** @deprecated Since 5.12 use {@link #getAllValues(TraceContext)} */
  @Deprecated public static List<BaggageField> getAll(TraceContextOrSamplingFlags extracted) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    return ExtraBaggageContext.getAllFields(extracted);
  }

  /** @deprecated Since 5.12 use {@link #getAllValues(TraceContext)} */
  @Deprecated @Nullable public static List<BaggageField> getAll() {
    return getAll(currentTraceContext());
  }

  /**
   * Returns a map of all {@linkplain BaggageField#name() name} to {@linkplain
   * BaggageField#getValue(TraceContext) non-{@code null} value} pairs in the {@linkplain
   * TraceContext.Extractor#extract(Object) extracted result}.
   *
   * @see #getAllValues(TraceContextOrSamplingFlags)
   * @since 5.12
   */
  public static Map<String, String> getAllValues(@Nullable TraceContext context) {
    if (context == null) return Collections.emptyMap();
    return ExtraBaggageContext.getAllValues(context);
  }

  /**
   * Returns a map of all {@linkplain BaggageField#name() name} to {@linkplain
   * BaggageField#getValue(TraceContextOrSamplingFlags) non-{@code null} value} pairs in the
   * {@linkplain TraceContext.Extractor#extract(Object) extracted result}.
   *
   * @see #getAllValues(TraceContext)
   * @since 5.12
   */
  public static Map<String, String> getAllValues(TraceContextOrSamplingFlags extracted) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    return ExtraBaggageContext.getAllValues(extracted);
  }

  /**
   * Like {@link #getAllValues(TraceContext)} except against the current trace context.
   *
   * <p>Prefer {@link #getAllValues(TraceContext)} if you have a reference to the trace context.
   *
   * @since 5.12
   */
  @Nullable public static Map<String, String> getAllValues() {
    return getAllValues(currentTraceContext());
  }

  /**
   * Looks up the field by {@code name}, useful for when you do not have a reference to it. In
   * general, {@link BaggageField}s should be referenced directly as constants where possible.
   *
   * @since 5.11
   */
  @Nullable public static BaggageField getByName(@Nullable TraceContext context, String name) {
    if (context == null) return null;
    return ExtraBaggageContext.getFieldByName(context, validateName(name));
  }

  /**
   * Looks up the field by {@code name}, useful for when you do not have a reference to it. In
   * general, {@link BaggageField}s should be referenced directly as constants where possible.
   *
   * @since 5.11
   */
  @Nullable public static BaggageField getByName(TraceContextOrSamplingFlags extracted,
      String name) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    return ExtraBaggageContext.getFieldByName(extracted, validateName(name));
  }

  /**
   * Like {@link #getByName(TraceContext, String)} except against the current trace context.
   *
   * <p>Prefer {@link #getByName(TraceContext, String)} if you have a reference to the trace
   * context.
   *
   * @since 5.11
   */
  @Nullable public static BaggageField getByName(String name) {
    return getByName(currentTraceContext(), name);
  }

  final String name, lcName;
  final BaggageContext context;

  BaggageField(String name, BaggageContext context) { // sealed to this package
    this.name = validateName(name);
    this.lcName = name.toLowerCase(Locale.ROOT);
    this.context = context;
  }

  /**
   * The non-empty name of the field. Ex "userId".
   *
   * <p>For example, if using log correlation and with field named "userId", the {@linkplain
   * #getValue(TraceContext) value} becomes the log variable {@code %{userId}} when the span is next
   * made current.
   *
   * @see #getByName(TraceContext, String)
   * @see CorrelationScopeConfig.SingleCorrelationField#name()
   * @since 5.11
   */
  public final String name() {
    return name;
  }

  /**
   * Returns the most recent value for this field in the context or null if unavailable.
   *
   * <p>The result may not be the same as the one {@link TraceContext.Extractor#extract(Object)
   * extracted} from the incoming context because {@link #updateValue(String)} can override it.
   *
   * @since 5.11
   */
  @Nullable public String getValue(@Nullable TraceContext context) {
    if (context == null) return null;
    return this.context.getValue(this, context);
  }

  /**
   * Like {@link #getValue(TraceContext)} except against the current trace context.
   *
   * <p>Prefer {@link #getValue(TraceContext)} if you have a reference to the trace context.
   *
   * @since 5.11
   */
  @Nullable public String getValue() {
    return getValue(currentTraceContext());
  }

  /**
   * Like {@link #getValue(TraceContext)} except for use cases that precede a span. For example, a
   * {@linkplain TraceContextOrSamplingFlags#traceIdContext() trace ID context}.
   *
   * @since 5.11
   */
  @Nullable public String getValue(TraceContextOrSamplingFlags extracted) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    return context.getValue(this, extracted);
  }

  /**
   * Updates the value of this field, or ignores if read-only or not configured.
   *
   * @since 5.11
   */
  public boolean updateValue(@Nullable TraceContext context, @Nullable String value) {
    if (context == null) return false;
    if (this.context.updateValue(this, context, value)) {
      CorrelationFlushScope.flush(this, value);
      return true;
    }
    return false;
  }

  /**
   * Like {@link #updateValue(TraceContext, String)} except for use cases that precede a span. For
   * example, a {@linkplain TraceContextOrSamplingFlags#traceIdContext() trace ID context}.
   *
   * @since 5.11
   */
  public boolean updateValue(TraceContextOrSamplingFlags extracted, @Nullable String value) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    if (context.updateValue(this, extracted, value)) {
      CorrelationFlushScope.flush(this, value);
      return true;
    }
    return false;
  }

  /**
   * Like {@link #updateValue(TraceContext, String)} except against the current trace context.
   *
   * <p>Prefer {@link #updateValue(TraceContext, String)} if you have a reference to the trace
   * context.
   *
   * @since 5.11
   */
  public boolean updateValue(String value) {
    return updateValue(currentTraceContext(), value);
  }

  @Override public String toString() {
    return "BaggageField{" + name + "}";
  }

  /** Returns true for any baggage field with the same name (case insensitive). */
  @Override public final boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof BaggageField)) return false;
    return lcName.equals(((BaggageField) o).lcName);
  }

  /** Returns the same value for any baggage field with the same name (case insensitive). */
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

  static {
    InternalBaggage.instance = new InternalBaggage() {
      @Override
      public BaggagePropagationConfig newBaggagePropagationConfig(
          BaggageCodec baggageCodec, int maxDynamicFields) {
        return new BaggagePropagationConfig(baggageCodec, maxDynamicFields);
      }
    };
  }
}
