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
import java.util.function.Function;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class ParameterizedSamplerTest {

  @Test public void matchesParameters() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.create(asList(rule(1.0f,
      Boolean::booleanValue)));

    assertThat(sampler.sample(true))
      .isEqualTo(SamplingFlags.SAMPLED);
  }

  @Test public void emptyOnNoMatch() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.create(asList(rule(1.0f,
      Boolean::booleanValue)));

    assertThat(sampler.sample(false))
      .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void emptyOnNull() {
    ParameterizedSampler<Void> sampler = ParameterizedSampler.create(asList(rule(1.0f, v -> true)));

    assertThat(sampler.sample(null))
      .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void multipleRules() {
    ParameterizedSampler<Boolean> sampler = ParameterizedSampler.create(asList(
      rule(1.0f, v -> false), // doesn't match
      rule(0.0f, v -> true) // match
    ));

    assertThat(sampler.sample(true))
      .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  // we can do this in tests because tests compile against java 8
  static <P> ParameterizedSampler.Rule<P> rule(float rate, Function<P, Boolean> rule) {
    return new ParameterizedSampler.Rule<P>(rate) {
      @Override public boolean matches(P parameters) {
        return rule.apply(parameters);
      }
    };
  }
}
