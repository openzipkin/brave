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
