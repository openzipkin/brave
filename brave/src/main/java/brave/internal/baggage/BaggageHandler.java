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
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.List;

/**
 * Handles context storage of one or more baggage values using a single {@linkplain #<S> state
 * value}. This is configured with {@link ExtraBaggageFields#newFactory(List)}.
 *
 * @param <S> the state that represents one or more baggage fields. <em>Note</em>: when {@link
 * String} on a single field handler, {@code null} should reflect empty state, as empty string ("")
 * is a valid baggage field value.
 */
public interface BaggageHandler<S> {
  StateDecoder<String> STRING_DECODER = new StateDecoder<String>() {
    @Override public <R> String decode(R request, String encoded) {
      return encoded;
    }
  };
  StateEncoder<String> STRING_ENCODER = new StateEncoder<String>() {
    @Override public <R> String encode(String state, TraceContext context, R request) {
      return state;
    }
  };

  /** @param <S> Same as {@link BaggageHandler#<S>} */
  interface StateDecoder<S> {
    /**
     * Called on the first non-{@code null} value from an {@link BaggagePropagationConfig#extractKeyNames()
     * extract key}. Decodes any field state from an extracted value or returns {@code null} if
     * there were none.
     *
     * <p>Ex. When the state is a simple string, this will just use the request value directly.
     * {@linkplain BaggageHandler#isDynamic() Dynamic values} will need to perform some decoding,
     * such as splitting on comma and equals.
     *
     * @param <R> the parameter from {@link Extractor#<R>}
     * @param request the parameter from {@link Extractor#extract(Object)}
     * @param encoded a non-{@code null} result of {@link Getter#get(Object, Object)}
     * @return the possibly {@code null} state to that will be assigned via {@link
     * ExtraBaggageFields#putState(BaggageHandler, Object)}
     * @see BaggagePropagationConfig#extractKeyNames()
     */
    @Nullable <R> S decode(R request, String encoded);
  }

  /** @param <S> Same as {@link BaggageHandler#<S>} */
  interface StateEncoder<S> {
    /**
     * Encodes any state to a request value used by {@link Setter#put(Object, Object, String)}. When
     * not {@code null}, the value will be used for all {@link BaggagePropagationConfig#injectKeyNames()}.
     *
     * <p>Ex. When the {@code state} is a simple string, this will just be returned with no change.
     * {@linkplain BaggageHandler#isDynamic() Dynamic values} will need to perform some encoding,
     * such as joining on equals and comma.
     *
     * @param <R> the parameter from {@link Injector#<R>}
     * @param state state returned by {@link ExtraBaggageFields#getState(BaggageHandler)}
     * @param request the parameter from {@link Injector#inject(TraceContext, Object)}
     * @return an input to {@link Setter#put(Object, Object, String)}
     * @see BaggagePropagationConfig#injectKeyNames()
     */
    @Nullable <R> String encode(S state, TraceContext context, R request);
  }

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
   * @see ExtraBaggageFields#getState(BaggageHandler)
   * @see BaggageField#getValue(TraceContext)
   * @see BaggageField#getValue(TraceContextOrSamplingFlags)
   */
  @Nullable String getValue(BaggageField field, S state);

  /**
   * Updates a state object to include a value change.
   *
   * <p>When the value is {@code null} and the input only includes the given field, either {@code
   * null} or an appropriate empty sentinel value should be returned.
   *
   * @param state {@code null} when there was formerly no state assigned to this handler
   * @param field the field that was updated
   * @param value {@code null} means remove the mapping to this field.
   * @return the possibly {@code null} value used as the {@code previous} parameter in the next call
   * to this method.
   * @see ExtraBaggageFields#putState(BaggageHandler, Object)
   * @see BaggageField#updateValue(TraceContext, String)
   * @see BaggageField#updateValue(TraceContextOrSamplingFlags, String)
   */
  @Nullable S updateState(@Nullable S state, BaggageField field, @Nullable String value);
}
