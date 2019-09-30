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

import org.junit.Test;

import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static brave.sampler.SamplerFunctions.nullSafe;
import static org.assertj.core.api.Assertions.assertThat;

public class SamplerFunctionsTest {
  @Test public void nullSafe_returnsNullOnNull() {
    assertThat(nullSafe(o -> {
      throw new NullPointerException();
    }).trySample(null)).isNull();
  }

  @Test public void nullSafe_passesOnNotNull() {
    assertThat(nullSafe(o -> true).trySample("1")).isTrue();
  }

  @Test public void nullSafe_doesntDoubleWrap() {
    SamplerFunction<Object> sampler = nullSafe(o -> {
      throw new NullPointerException();
    });
    assertThat(sampler).isSameAs(sampler);
  }

  @Test public void nullSafe_doesntWrapConstants() {
    assertThat(nullSafe(deferDecision())).isSameAs(deferDecision());
    assertThat(nullSafe(neverSample())).isSameAs(neverSample());
  }

  @Test public void deferDecision_returnsNull() {
    assertThat(deferDecision().trySample(null)).isNull();
    assertThat(deferDecision().trySample("1")).isNull();
  }

  @Test public void neverSample_returnsFalse() {
    assertThat(neverSample().trySample(null)).isFalse();
    assertThat(neverSample().trySample("1")).isFalse();
  }
}
