/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.sampler;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.data.Percentage.withPercentage;

class BoundarySamplerTest extends SamplerTest {
  @Override Sampler newSampler(float probability) {
    return BoundarySampler.create(probability);
  }

  @Override Percentage expectedErrorProbability() {
    return withPercentage(10);
  }

  @Test void acceptsOneInTenThousandProbability() {
    newSampler(0.0001f);
  }
}
