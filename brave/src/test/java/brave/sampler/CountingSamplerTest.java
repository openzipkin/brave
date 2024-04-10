/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.sampler;

import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.data.Percentage.withPercentage;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountingSamplerTest extends SamplerTest {
  @Override Sampler newSampler(float probability) {
    return CountingSampler.create(probability);
  }

  @Override Percentage expectedErrorProbability() {
    return withPercentage(0);
  }

  @Test void probabilityMinimumOnePercent() {
    assertThrows(IllegalArgumentException.class, () -> {
      newSampler(0.0001f);
    });
  }
}
