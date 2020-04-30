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
import brave.propagation.Propagation.Getter;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Collections;
import java.util.List;

public interface BaggageCodec {
  /**
   * Use this when configuration results in no codec needed.
   */
  BaggageCodec NOOP = new BaggageCodec() {
    @Override public List<String> extractKeyNames() {
      return Collections.emptyList();
    }

    @Override public List<String> injectKeyNames() {
      return Collections.emptyList();
    }

    @Override public boolean decode(ExtraBaggageFields extra, Object request, String value) {
      return false;
    }

    @Override public String encode(ExtraBaggageFields extra, TraceContext context, Object request) {
      return null;
    }

    @Override public String toString() {
      return "NoopBaggageCodec";
    }
  };

  /**
   * Ordered list of key names used during {@link Extractor#extract(Object)} with {@link
   * Getter#get(Object, Object)}.
   *
   * <p>{@link Getter#get(Object, Object)} will be called against these in order until a
   * non-{@code null} value result or there are no more keys.
   *
   * @since 5.12
   */
  List<String> extractKeyNames();

  /**
   * Ordered list of key names used during {@link Injector#inject(TraceContext, Object)} with {@link
   * Setter#put(Object, Object, String)}.
   *
   * @since 5.12
   */
  List<String> injectKeyNames();

  /**
   * Called on the first non-{@code null} value from an {@link #extractKeyNames() extract key}.
   * Decodes any field state from an extracted value or returns {@code null} if there were none.
   *
   * <p>Ex. When the state is a simple string, this will just use the request value directly.
   * {@linkplain ExtraBaggageFields#isDynamic() dynamic values} will need to perform some decoding,
   * such as splitting on comma and equals.
   *
   * @param extra holds {@link BaggageField} state.
   * @param request the parameter of {@link Extractor#extract(Object)}
   * @param value a non-{@code null} result of {@link Getter#get(Object, Object)}
   * @see #extractKeyNames()
   */
  boolean decode(ExtraBaggageFields extra, Object request, String value);

  /**
   * Encodes any state to a request value used by {@link Setter#put(Object, Object, String)}. When
   * not {@code null}, the value will be used for all {@link #injectKeyNames()}.
   *
   * <p>Ex. When the {@code state} is a simple string, this will just be returned with no change.
   * {@linkplain ExtraBaggageFields#isDynamic() Dynamic values} will need to perform some encoding,
   * such as joining on equals and comma.
   *
   * @param extra holds {@link BaggageField} state.
   * @return an input to {@link Setter#put(Object, Object, String)}
   * @see #injectKeyNames()
   */
  @Nullable String encode(ExtraBaggageFields extra, TraceContext context, Object request);
}
