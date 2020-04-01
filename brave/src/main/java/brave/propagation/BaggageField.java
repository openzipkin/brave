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

import brave.Tracing;
import brave.internal.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static brave.propagation.TraceContext.findExtra;
import static java.util.Arrays.asList;

/**
 * Defines a request-scoped field, usually but not always analogous to an HTTP header. Fields will
 * be no-op unless {@link BaggagePropagation} is configured.
 *
 * <p>For example, if you have a need to know a specific request's country code in a downstream
 * service, you can propagate it through the trace:
 * <pre>{@code
 * // Configure your baggage field
 * COUNTRY_CODE = BaggageField.create("country-code");
 * }</pre>
 *
 * <p>If you don't have a reference to a baggage field, you can use {@linkplain
 * #getByName(TraceContext, String)}.
 *
 * <h3>Local Usage</h3>
 * As long as a field is configured with {@link BaggagePropagation}, local reads and updates are
 * possible in-process.
 *
 * <p>You can also integrate baggage with other correlated contexts such as logging:
 * <pre>{@code
 * AMZN_TRACE_ID = BaggageField.newBuilder("x-amzn-trace-id").build();
 *
 * // Allow logging patterns like %X{traceId} %X{x-amzn-trace-id}
 * decorator = MDCScopeDecorator.newBuilder()
 *                              .addField(AMZN_TRACE_ID).build();
 *
 * tracingBuilder.propagationFactory(BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
 *                                                     .addField(AMZN_TRACE_ID)
 *                                                     .build())
 *               .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
 *                                                                  .addScopeDecorator(decorator)
 *                                                                  .build())
 * }</pre>
 *
 * <h3>Customizing remote names</h3>
 * By default the name used for remote propagation (header) is the same as the lowercase variant of
 * the field name. You can override this using the builder.
 *
 * For example, the following will propagate the field "x-vcap-request-id" as-is, but send the
 * fields "countryCode" and "userId" on the wire as "baggage-country-code" and "baggage-user-id"
 * respectively.
 * <pre>{@code
 * REQUEST_ID = BaggageField.create("x-vcap-request-id");
 * COUNTRY_CODE = BaggageField.newBuilder("countryCode").clearRemoteNames()
 *                            .addRemoteName("baggage-country-code").build();
 * USER_ID = BaggageField.newBuilder("userId").clearRemoteNames()
 *                       .addRemoteName("baggage-user-id").build();
 * }</pre>
 *
 * <p><em>Note:</em> Empty remote names is permitted. In this case, the baggage is only available
 * for local correlation purposes.
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
 * @see BaggagePropagation
 * @see CorrelationScopeDecorator
 * @since 5.11
 */
public final class BaggageField {
  /**
   * Creates a field that is referenced the same in-process as it is on the wire. For example, the
   * name "x-vcap-request-id" would be set as-is including the prefix.
   *
   * @param name will be lower-cased for remote propagation
   * @since 5.11
   */
  public static BaggageField create(String name) {
    return new Builder(name).build();
  }

  /**
   * Creates a builder for the specified {@code name}.
   *
   * @param name will be lower-cased for remote propagation
   * @since 5.11
   */
  public static Builder newBuilder(String name) {
    return new Builder(name);
  }

  /**
   * Gets any fields in the in given trace context.
   *
   * @since 5.11
   */
  public static List<BaggageField> getAll(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    PredefinedBaggageFields fields = context.findExtra(PredefinedBaggageFields.class);
    if (fields == null) return Collections.emptyList();
    return Collections.unmodifiableList(asList(fields.fields));
  }

  /**
   * Gets any fields in the in the {@linkplain TraceContext.Extractor#extract(Object) extracted
   * result}.
   *
   * @since 5.11
   */
  public static List<BaggageField> getAll(TraceContextOrSamplingFlags extracted) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    if (extracted.context() != null) return getAll(extracted.context());
    PredefinedBaggageFields fields = findExtra(PredefinedBaggageFields.class, extracted.extra());
    if (fields == null) return Collections.emptyList();
    return Collections.unmodifiableList(asList(fields.fields));
  }

  /**
   * Like {@link #getAll(TraceContext)} except against the current trace context.
   *
   * <p>Prefer {@link #getAll(TraceContext)} if you have a reference to the trace context.
   *
   * @since 5.11
   */
  @Nullable public static List<BaggageField> getAll() {
    TraceContext context = currentTraceContext();
    return context != null ? getAll(context) : Collections.emptyList();
  }

  /**
   * Looks up the field by {@code name}, useful for when you do not have a reference to it. In
   * general, {@link BaggageField}s should be referenced directly as constants where possible.
   *
   * @since 5.11
   */
  @Nullable public static BaggageField getByName(TraceContext context, String name) {
    name = validateName(name);
    PredefinedBaggageFields fields = context.findExtra(PredefinedBaggageFields.class);
    return getByName(fields, name);
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
    if (extracted.context() != null) return getByName(extracted.context(), name);
    PredefinedBaggageFields fields = findExtra(PredefinedBaggageFields.class, extracted.extra());
    return getByName(fields, name);
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
    name = validateName(name);
    TraceContext context = currentTraceContext();
    return context != null ? getByName(context, name) : null;
  }

  /** @since 5.11 */
  public static class Builder {
    final String name;
    final Set<String> remoteNames = new LinkedHashSet<>();
    ValueAccessor valueAccessor = ValueFromExtra.INSTANCE;
    boolean flushOnUpdate = false, readOnly = false;

    Builder(String name) {
      this.name = validateName(name);
      remoteNames.add(this.name.toLowerCase(Locale.ROOT));
    }

    /**
     * Invoke this to clear propagated names of this field. You can add alternatives later with
     * {@link #addRemoteName(String)}. <p>The default propagated name is the lowercase variant of
     * the field name.
     *
     * <p>One use case is prefixing. You may wish to not propagate the plain name of this field,
     * rather only a prefixed name in hyphen case. For example, the following would make the field
     * named "userId" propagated only as "baggage-user-id".
     *
     * <pre>{@code
     * USER_ID = BaggageField.newBuilder("userId")
     *                       .clearRemoteNames()
     *                       .addRemoteName("baggage-user-id").build();
     * }</pre>
     *
     * <p>Another use case is local-only baggage. When there are no remote names, the field can
     * still be used in {@link CorrelationScopeDecorator}.
     *
     * @since 5.11
     */
    public Builder clearRemoteNames() {
      remoteNames.clear();
      return this;
    }

    /**
     * Adds a {@linkplain #remoteNames() remote name} (header).
     *
     * <p>Note: remote names are implicitly lower-cased.
     *
     * @since 5.11
     */
    public Builder addRemoteName(String remoteName) {
      remoteNames.add(validateName(remoteName).toLowerCase(Locale.ROOT));
      return this;
    }

    /**
     * @see BaggageField#readOnly()
     * @since 5.11
     */
    public Builder readOnly() {
      this.readOnly = true;
      return this;
    }

    /**
     * @see BaggageField#flushOnUpdate()
     * @since 5.11
     */
    public Builder flushOnUpdate() {
      this.flushOnUpdate = true;
      return this;
    }

    /** @since 5.11 */
    public BaggageField build() {
      if (readOnly && flushOnUpdate) {
        throw new IllegalArgumentException("a field cannot be both readOnly and flushOnUpdate");
      }
      return new BaggageField(this);
    }

    Builder internalValueAccessor(ValueAccessor valueAccessor) {
      this.valueAccessor = valueAccessor;
      return this;
    }
  }

  final String name, lcName;
  final ValueAccessor valueAccessor;
  final String[] remoteNames; // for faster iteration
  final List<String> remoteNameList;
  final boolean readOnly, flushOnUpdate;

  BaggageField(Builder builder) { // sealed to this package
    name = builder.name;
    lcName = name.toLowerCase(Locale.ROOT);
    valueAccessor = builder.valueAccessor;
    readOnly = builder.readOnly;
    flushOnUpdate = builder.flushOnUpdate;
    remoteNames = builder.remoteNames.toArray(new String[0]);
    remoteNameList = Collections.unmodifiableList(asList(remoteNames));
  }

  /**
   * The non-empty name of the field. Ex "userId".
   *
   * <p>For example, if using log correlation and with field named "userId", the {@linkplain
   * #getValue(TraceContext) value} becomes the log variable {@code %{userId}} when the span is next
   * made current.
   *
   * @see CorrelationScopeDecorator
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
  @Nullable public String getValue(TraceContext context) {
    if (context == null) throw new NullPointerException("context == null");
    return valueAccessor.get(this, context);
  }

  /**
   * Like {@link #getValue(TraceContext)} except against the current trace context.
   *
   * <p>Prefer {@link #getValue(TraceContext)} if you have a reference to the trace context.
   *
   * @since 5.11
   */
  @Nullable public String getValue() {
    TraceContext context = currentTraceContext();
    return context != null ? getValue(context) : null;
  }

  /**
   * Like {@link #getValue(TraceContext)} except for use cases that precede a span. For example, a
   * {@linkplain TraceContextOrSamplingFlags#traceIdContext() trace ID context}.
   *
   * @since 5.11
   */
  @Nullable public String getValue(TraceContextOrSamplingFlags extracted) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    return valueAccessor.get(this, extracted);
  }

  /**
   * Updates the value of the this field, or ignores if {@link #readOnly or} not configured.
   *
   * @since 5.11
   */
  public void updateValue(TraceContext context, @Nullable String value) {
    if (context == null) throw new NullPointerException("context == null");
    if (readOnly) return;
    updateValue(context.extra(), value);
  }

  /**
   * Like {@link #updateValue(TraceContextOrSamplingFlags, String)} except for use cases that
   * precede a span. For example, a {@linkplain TraceContextOrSamplingFlags#traceIdContext() trace
   * ID context}.
   *
   * @since 5.11
   */
  public void updateValue(TraceContextOrSamplingFlags extracted, @Nullable String value) {
    if (extracted == null) throw new NullPointerException("extracted == null");
    if (readOnly) return;
    updateValue(extra(extracted), value);
  }

  void updateValue(List<Object> extra, @Nullable String value) {
    PredefinedBaggageFields fields = findExtra(PredefinedBaggageFields.class, extra);
    if (fields == null) return;
    fields.put(this, value);
    if (flushOnUpdate) BaggageFieldFlushScope.flush(this, value);
  }

  /**
   * Like {@link #updateValue(TraceContext, String)} except against the current trace context.
   *
   * <p>Prefer {@link #updateValue(TraceContext, String)} if you have a reference to the trace
   * context.
   *
   * @since 5.11
   */
  public void updateValue(String value) {
    if (readOnly) return;
    TraceContext context = currentTraceContext();
    if (context != null) updateValue(context, value);
  }

  /**
   * When true, updates to this field are ignored.
   *
   * @since 5.11
   */
  public final boolean readOnly() {
    return readOnly;
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
   * fields such as {@link BaggageFields#SPAN_ID the span ID} and {@linkplain
   * BaggageFields#constant(String, String) constants} such as env variables do not need to be
   * tracked. Even field value updates do not necessarily need to be flushed to the underlying
   * correlation context, as they will apply on the next scope operation.
   *
   * @since 5.11
   */
  public final boolean flushOnUpdate() {
    return flushOnUpdate;
  }

  /**
   * The possibly empty list of names for use in remote propagation. These are typically header
   * names. By default this includes only the lowercase variant of the {@link #name()}.
   *
   * @see BaggagePropagation#keys()
   * @since 5.11
   */
  public List<String> remoteNames() {
    return remoteNameList;
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

  static List<Object> extra(TraceContextOrSamplingFlags extracted) {
    return extracted.context() != null ? extracted.context().extra() : extracted.extra();
  }

  /** Internal type that allows baggage accessors for defined fields in the Trace context. */
  interface ValueAccessor {
    String get(BaggageField field, TraceContextOrSamplingFlags extracted);

    String get(BaggageField field, TraceContext context);
  }

  enum ValueFromExtra implements ValueAccessor {
    INSTANCE;

    @Override public String get(BaggageField field, TraceContextOrSamplingFlags extracted) {
      if (extracted.context() != null) return get(field, extracted.context());
      return get(field, extracted.extra());
    }

    @Override public String get(BaggageField field, TraceContext context) {
      return get(field, context.extra());
    }

    static String get(BaggageField field, List<Object> extra) {
      PredefinedBaggageFields fields = findExtra(PredefinedBaggageFields.class, extra);
      if (fields == null) return null;
      return fields.get(field);
    }
  }

  @Nullable static BaggageField getByName(PredefinedBaggageFields fields, String name) {
    if (fields == null) return null;
    for (BaggageField field : fields.fields) {
      if (name.equals(field.name())) {
        return field;
      }
    }
    return null;
  }
}
