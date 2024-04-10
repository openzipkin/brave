/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.baggage;

import brave.baggage.BaggageField;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;

/** Internal type that implements context storage for the field. */
public abstract class BaggageContext {
  @Nullable
  public abstract String getValue(BaggageField field, TraceContextOrSamplingFlags extracted);

  @Nullable public abstract String getValue(BaggageField field, TraceContext context);

  /** Returns false if the update was ignored. */
  public abstract boolean updateValue(BaggageField field, TraceContextOrSamplingFlags extracted,
    @Nullable String value);

  /** Returns false if the update was ignored. */
  public abstract boolean updateValue(BaggageField field, TraceContext context,
    @Nullable String value);

  /** Appropriate for constants or immutable fields defined in {@link TraceContext}. */
  public static abstract class ReadOnly extends BaggageContext {
    @Override public boolean updateValue(BaggageField field, TraceContextOrSamplingFlags extracted,
      @Nullable String value) {
      return false;
    }

    @Override public boolean updateValue(BaggageField field, TraceContext context, String value) {
      return false;
    }
  }
}
