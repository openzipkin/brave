/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
package brave.sampler;

import java.util.Random;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class SamplerTest {
  /**
   * Zipkin trace ids are random 64bit numbers. This creates a relatively large input to avoid
   * flaking out due to PRNG nuance.
   */
  static final int INPUT_SIZE = 100000;

  abstract Sampler newSampler(float probability);

  abstract Percentage expectedErrorProbability();

  @ParameterizedTest
  @ValueSource(floats = {0.01f, 0.5f, 0.9f})
  void retainsPerSampleProbability(float sampleProbability) {
    final Sampler sampler = newSampler(sampleProbability);

    // parallel to ensure there aren't any unsynchronized race conditions
    long passed = new Random().longs(INPUT_SIZE).parallel().filter(sampler::isSampled).count();

    assertThat(passed)
      .isCloseTo((long) (INPUT_SIZE * sampleProbability), expectedErrorProbability());
  }

  @Test void zeroMeansDropAllTraces() {
    final Sampler sampler = newSampler(0.0f);

    assertThat(new Random().longs(INPUT_SIZE).filter(sampler::isSampled).findAny()).isEmpty();
  }

  @Test void oneMeansKeepAllTraces() {
    final Sampler sampler = newSampler(1.0f);

    assertThat(new Random().longs(INPUT_SIZE).filter(sampler::isSampled).count()).isEqualTo(
      INPUT_SIZE);
  }

  @Test void probabilityCantBeNegative() {
    assertThrows(IllegalArgumentException.class, () -> {
      newSampler(-1.0f);
    });
  }

  @Test void probabilityCantBeOverOne() {
    assertThrows(IllegalArgumentException.class, () -> {
      newSampler(1.1f);
    });
  }
}
