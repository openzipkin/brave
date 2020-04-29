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
package brave.internal;

import brave.baggage.BaggageField;
import brave.baggage.BaggagePropagationConfig;
import brave.internal.baggage.BaggageHandler;

/**
 * Escalate internal APIs in {@code brave.baggage} so they can be used from outside packages. The
 * only implementation is in {@link BaggageField}.
 *
 * <p>Inspired by {@code okhttp3.internal.Internal}.
 */
public abstract class InternalBaggage {
  public static InternalBaggage instance;

  /**
   * {@link BaggageHandler} is not yet a public api, but it is being tested with secondary
   * sampling.
   *
   * <p>See https://github.com/openzipkin-contrib/zipkin-secondary-sampling
   */
  public abstract <S> BaggagePropagationConfig<S> newBaggagePropagationConfig(
      String keyName,
      BaggageHandler<S> baggageHandler,
      BaggageHandler.StateDecoder<S> stateDecoder,
      BaggageHandler.StateEncoder<S> stateEncoder
  );
}
