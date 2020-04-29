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
import brave.baggage.BaggagePropagationConfig;
import brave.internal.Nullable;
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;

/**
 * Handles context storage of one or more baggage fields using one {@link ExtraBaggageFields}
 * state.
 *
 * @param <S> the state that represents one or more baggage fields
 */
public interface RemoteBaggageHandler<S> extends BaggageHandler<S> {
  /**
   * Extracts any state from a request value received by {@link Getter#get(Object, Object)}.
   *
   * <p>Ex. When the state is a simple string, this will just use the request value directly.
   * {@linkplain #isDynamic() Dynamic values} will need to perform some decoding, such as splitting
   * on comma and equals.
   *
   * @see Getter
   * @see BaggagePropagationConfig.SingleBaggageField#remote(BaggageField)
   */
  @Nullable S fromRemoteValue(Object request, String value);

  /**
   * Converts any state to a request value used by {@link Setter#put(Object, Object, String)}.
   *
   * <p>Ex. When the state is a simple string, this will just be returned with no change.
   * {@linkplain #isDynamic() Dynamic values} will need to perform some encoding, such as joining on
   * equals and comma.
   *
   * @see Setter
   * @see BaggagePropagationConfig.SingleBaggageField#remote(BaggageField)
   */
  String toRemoteValue(S state);
}
