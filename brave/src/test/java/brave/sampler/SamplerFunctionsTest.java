/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.sampler;

import org.junit.jupiter.api.Test;

import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static brave.sampler.SamplerFunctions.nullSafe;
import static org.assertj.core.api.Assertions.assertThat;

class SamplerFunctionsTest {
  @Test void nullSafe_returnsNullOnNull() {
    assertThat(nullSafe(o -> {
      throw new NullPointerException();
    }).trySample(null)).isNull();
  }

  @Test void nullSafe_passesOnNotNull() {
    assertThat(nullSafe(o -> true).trySample("1")).isTrue();
  }

  @Test void nullSafe_doesntDoubleWrap() {
    SamplerFunction<Object> sampler = nullSafe(o -> {
      throw new NullPointerException();
    });
    assertThat(sampler).isSameAs(sampler);
  }

  @Test void nullSafe_doesntWrapConstants() {
    assertThat(nullSafe(deferDecision())).isSameAs(deferDecision());
    assertThat(nullSafe(neverSample())).isSameAs(neverSample());
  }

  @Test void deferDecision_returnsNull() {
    assertThat(deferDecision().trySample(null)).isNull();
    assertThat(deferDecision().trySample("1")).isNull();
  }

  @Test void neverSample_returnsFalse() {
    assertThat(neverSample().trySample(null)).isFalse();
    assertThat(neverSample().trySample("1")).isFalse();
  }
}
