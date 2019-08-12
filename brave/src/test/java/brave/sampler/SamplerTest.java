/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Theories.class)
public abstract class SamplerTest {
  /**
   * Zipkin trace ids are random 64bit numbers. This creates a relatively large input to avoid
   * flaking out due to PRNG nuance.
   */
  static final int INPUT_SIZE = 100000;

  abstract Sampler newSampler(float rate);

  abstract Percentage expectedErrorRate();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @DataPoints
  public static final float[] SAMPLE_RATES = {0.01f, 0.5f, 0.9f};

  @Theory
  public void retainsPerSampleRate(float sampleRate) {
    final Sampler sampler = newSampler(sampleRate);

    // parallel to ensure there aren't any unsynchronized race conditions
    long passed = new Random().longs(INPUT_SIZE).parallel().filter(sampler::isSampled).count();

    assertThat(passed)
      .isCloseTo((long) (INPUT_SIZE * sampleRate), expectedErrorRate());
  }

  @Test
  public void zeroMeansDropAllTraces() {
    final Sampler sampler = newSampler(0.0f);

    assertThat(new Random().longs(INPUT_SIZE).filter(sampler::isSampled).findAny()).isEmpty();
  }

  @Test
  public void oneMeansKeepAllTraces() {
    final Sampler sampler = newSampler(1.0f);

    assertThat(new Random().longs(INPUT_SIZE).filter(sampler::isSampled).count()).isEqualTo(
      INPUT_SIZE);
  }

  @Test
  public void rateCantBeNegative() {
    thrown.expect(IllegalArgumentException.class);

    newSampler(-1.0f);
  }

  @Test
  public void rateCantBeOverOne() {
    thrown.expect(IllegalArgumentException.class);

    newSampler(1.1f);
  }
}
