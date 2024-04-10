/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.baggage;

import brave.internal.baggage.BaggageCodec;

public abstract class Access {
  /** {@link BaggageCodec} is not yet a public api */
  public static BaggagePropagationConfig newBaggagePropagationConfig(
      BaggageCodec baggageCodec, int maxDynamicFields) {
    return new BaggagePropagationConfig(baggageCodec, maxDynamicFields);
  }
}
