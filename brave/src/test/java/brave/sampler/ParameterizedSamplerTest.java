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

import brave.propagation.SamplingFlags;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterizedSamplerTest {

  @Test public void matchesParameters() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.<Boolean>newBuilder()
      .putRule(Boolean::booleanValue, Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.sample(true))
      .isEqualTo(SamplingFlags.SAMPLED);
  }

  @Test public void emptyOnNoMatch() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.<Boolean>newBuilder()
      .putRule(Boolean::booleanValue, Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.sample(false))
      .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void emptyOnNull() {
    ParameterizedSampler<Void> sampler = ParameterizedSampler.<Void>newBuilder()
      .putRule(v -> true, Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.sample(null))
      .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void multipleRules() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.<Boolean>newBuilder()
      .putRule(v -> false, Sampler.ALWAYS_SAMPLE) // doesn't match
      .putRule(v -> true, Sampler.NEVER_SAMPLE) // match
      .build();

    assertThat(sampler.sample(true))
      .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void putAllRules() {
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
  @Test public void noRulesOk() {
    ParameterizedSampler.<Boolean>newBuilder().build();
  }
}
