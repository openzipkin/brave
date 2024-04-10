/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.sampler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParameterizedSamplerTest {

  @Test void matchesParameters() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.<Boolean>newBuilder()
      .putRule(Boolean::booleanValue, Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.trySample(true))
      .isTrue();
  }

  @Test void emptyOnNoMatch() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.<Boolean>newBuilder()
      .putRule(Boolean::booleanValue, Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.trySample(false))
      .isNull();
  }

  @Test void emptyOnNull() {
    ParameterizedSampler<Void> sampler = ParameterizedSampler.<Void>newBuilder()
      .putRule(v -> true, Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.trySample(null))
      .isNull();
  }

  @Test void nullOnNull() {
    ParameterizedSampler<Void> sampler = ParameterizedSampler.<Void>newBuilder()
      .putRule(v -> true, Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.trySample(null))
      .isNull();
  }

  @Test void multipleRules() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.<Boolean>newBuilder()
      .putRule(v -> false, Sampler.ALWAYS_SAMPLE) // doesn't match
      .putRule(v -> true, Sampler.NEVER_SAMPLE) // match
      .build();

    assertThat(sampler.trySample(true))
      .isFalse();
  }

  @Test void putAllRules() {
    Matcher<Void> one = v -> false;
    Matcher<Void> two = v -> true;
    Matcher<Void> three = v -> Boolean.FALSE;
    Matcher<Void> four = v -> Boolean.TRUE;
    ParameterizedSampler<Void> base = ParameterizedSampler.<Void>newBuilder()
      .putRule(one, Sampler.ALWAYS_SAMPLE)
      .putRule(two, Sampler.NEVER_SAMPLE)
      .putRule(three, Sampler.ALWAYS_SAMPLE)
      .build();

    ParameterizedSampler<Void> extended = ParameterizedSampler.<Void>newBuilder()
      .putAllRules(base)
      .putRule(one, Sampler.NEVER_SAMPLE)
      .putRule(four, Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(extended).usingRecursiveComparison()
      .isEqualTo(ParameterizedSampler.<Void>newBuilder()
        .putRule(one, Sampler.NEVER_SAMPLE)
        .putRule(two, Sampler.NEVER_SAMPLE)
        .putRule(three, Sampler.ALWAYS_SAMPLE)
        .putRule(four, Sampler.ALWAYS_SAMPLE)
        .build()
      );
  }

  // empty may sound unintuitive, but it allows use of the same type when always deferring
  @Test void noRulesOk() {
    ParameterizedSampler.<Boolean>newBuilder().build();
  }
}
