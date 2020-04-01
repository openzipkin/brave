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

import brave.internal.Nullable;

/**
 * Common fields accessible
 *
 * @since 5.11
 */
public final class BaggageFields {
  /**
   * This is the most common log correlation field.
   *
   * @see TraceContext#traceIdString()
   * @since 5.11
   */
  public static final BaggageField TRACE_ID = BaggageField.newBuilder("traceId")
    .internalValueAccessor(TraceIdAccessor.INSTANCE)
    .readOnly().clearRemoteNames().build();

  enum TraceIdAccessor implements BaggageField.ValueAccessor {
    INSTANCE;

    @Override public String get(BaggageField field, TraceContextOrSamplingFlags extracted) {
      if (extracted.context() != null) return get(field, extracted.context());
      if (extracted.traceIdContext() != null) extracted.traceIdContext().traceIdString();
      return null;
    }

    @Override public String get(BaggageField field, TraceContext context) {
      return context.traceIdString();
    }
  }

  /**
   * Typically only useful when spans are parsed from log records.
   *
   * @see TraceContext#parentIdString()
   * @since 5.11
   */
  public static final BaggageField PARENT_ID = BaggageField.newBuilder("parentId")
    .internalValueAccessor(ParentIdAccessor.INSTANCE)
    .readOnly().clearRemoteNames().build();

  enum ParentIdAccessor implements BaggageField.ValueAccessor {
    INSTANCE;

    @Override public String get(BaggageField field, TraceContextOrSamplingFlags extracted) {
      if (extracted.context() != null) return get(field, extracted.context());
      return null;
    }

    @Override public String get(BaggageField field, TraceContext context) {
      return context.parentIdString();
    }
  }

  /**
   * Used with {@link #TRACE_ID} to correlate a log line with a span.
   *
   * @see TraceContext#spanIdString()
   * @since 5.11
   */
  public static final BaggageField SPAN_ID = BaggageField.newBuilder("spanId")
    .internalValueAccessor(SpanIdAccessor.INSTANCE)
    .readOnly().clearRemoteNames().build();

  enum SpanIdAccessor implements BaggageField.ValueAccessor {
    INSTANCE;

    @Override public String get(BaggageField field, TraceContextOrSamplingFlags extracted) {
      if (extracted.context() != null) return get(field, extracted.context());
      return null;
    }

    @Override public String get(BaggageField field, TraceContext context) {
      return context.spanIdString();
    }
  }

  /**
   * This is only useful when {@link #TRACE_ID} is also a baggage field. It is a hint that a trace
   * may exist in Zipkin, when a user is viewing logs. For example, unsampled traces are not
   * typically reported to Zipkin.
   *
   * @see TraceContext#sampled()
   * @since 5.11
   */
  public static final BaggageField SAMPLED = BaggageField.newBuilder("sampled")
    .internalValueAccessor(SampledAccessor.INSTANCE)
    .readOnly().clearRemoteNames().build();

  enum SampledAccessor implements BaggageField.ValueAccessor {
    INSTANCE;

    @Override public String get(BaggageField field, TraceContextOrSamplingFlags extracted) {
      return getValue(extracted.sampled());
    }

    @Override public String get(BaggageField field, TraceContext context) {
      return getValue(context.sampled());
    }

    @Nullable static String getValue(@Nullable Boolean sampled) {
      return sampled != null ? sampled.toString() : null;
    }
  }

  /**
   * Creates a local baggage field based on a possibly null constant, such as an ENV variable.
   *
   * <p>Ex.
   * <pre>{@code
   * CLOUD_REGION = BaggageFields.constant("region", System.getEnv("CLOUD_REGION"));
   * }</pre>
   *
   * @since 5.11
   */
  public static BaggageField constant(String name, @Nullable String value) {
    return BaggageField.newBuilder(name)
      .internalValueAccessor(new ConstantValueAccessor(value))
      .readOnly().clearRemoteNames().build();
  }

  static final class ConstantValueAccessor implements BaggageField.ValueAccessor {
    @Nullable final String value;

    ConstantValueAccessor(String value) {
      this.value = value;
    }

    @Override public String get(BaggageField field, TraceContextOrSamplingFlags extracted) {
      return value;
    }

    @Override public String get(BaggageField field, TraceContext context) {
      return value;
    }
  }
}
