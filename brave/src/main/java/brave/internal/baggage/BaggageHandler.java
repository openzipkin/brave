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
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.List;

/**
 * Handles context storage of one or more baggage fields using one {@link ExtraBaggageFields}
 * state.
 *
 * @param <S> the state that represents one or more baggage fields
 */
public interface BaggageHandler<S> {
  /**
   * When true, calls to {@link #currentFields(Object)} and {@link #handlesField(BaggageField)}
   * cannot be cached.
   */
  boolean isDynamic();

  /** Don't cache if {@link #isDynamic()}. If not dynamic, the state parameter can be ignored. */
  List<BaggageField> currentFields(@Nullable S state);

  /** Don't cache if {@link #isDynamic()}. */
  boolean handlesField(BaggageField field);

  /**
   * Gets the value of this field in the given state.
   *
   * @see BaggageField#getValue(TraceContext)
   * @see BaggageField#getValue(TraceContextOrSamplingFlags)
   */
  @Nullable String getValue(BaggageField field, S state);

  /**
   * Creates a state object to support the first field value.
   *
   * @see BaggageField#updateValue(TraceContext, String)
   * @see BaggageField#updateValue(TraceContextOrSamplingFlags, String)
   */
  S newState(BaggageField field, String value);

  /**
   * Updates a state object to include a value change.
   *
   * <p>When the value is {@code null} and the input only includes the given field, either {@code
   * null} or an appropriate empty sentinel value should be returned.
   *
   * <p>When {@code null} is returned, the next non-null update results in a call to {@link
   * #newState}. Otherwise, the empty state object will become the next input parameter here.
   *
   * @see BaggageField#updateValue(TraceContext, String)
   * @see BaggageField#updateValue(TraceContextOrSamplingFlags, String)
   */
  @Nullable S updateState(S state, BaggageField field, @Nullable String value);

  /**
   * Extracts any state from a remote value received by {@link Propagation.Getter#get(Object,
   * Object)}.
   *
   * <p>Ex. When the state is a simple string, this will just use the remote value directly.
   * {@linkplain #isDynamic() Dynamic values} will need to perform some decoding, such as splitting
   * on comma and equals.
   */
  @Nullable S fromRemoteValue(String remoteValue);

  /**
   * Converts any state to a remote value used by {@link Propagation.Setter#put(Object, Object,
   * String)}.
   *
   * <p>Ex. When the state is a simple string, this will just be returned with no change.
   * {@linkplain #isDynamic() Dynamic values} will need to perform some encoding, such as joining on
   * equals and comma.
   */
  String toRemoteValue(S state);
}
