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
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeclarativeSamplerTest {

  DeclarativeSampler<Traced> declarativeSampler =
    DeclarativeSampler.create(t -> t.enabled() ? t.sampleRate() : null);

  @Before public void clear() {
    declarativeSampler.methodsToSamplers.clear();
  }

  @Test public void honorsSampleRate() {
    assertThat(declarativeSampler.sample(traced(1.0f, true)))
      .isEqualTo(SamplingFlags.SAMPLED);

    assertThat(declarativeSampler.sample(traced(0.0f, true)))
      .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void acceptsFallback() {
    assertThat(declarativeSampler.sample(traced(1.0f, false)))
      .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void toSampler() {
    assertThat(declarativeSampler.toSampler(traced(1.0f, true)).isSampled(0L))
      .isTrue();

    assertThat(declarativeSampler.toSampler(traced(0.0f, true)).isSampled(0L))
      .isFalse();

    // check not enabled is false
    assertThat(declarativeSampler.toSampler(traced(1.0f, false)).isSampled(0L))
      .isFalse();
  }

  @Test public void toSampler_fallback() {
    Sampler withFallback = declarativeSampler.toSampler(traced(1.0f, false), Sampler.ALWAYS_SAMPLE);

    assertThat(withFallback.isSampled(0L))
      .isTrue();
  }

  @Test public void samplerLoadsLazy() {
    assertThat(declarativeSampler.methodsToSamplers)
      .isEmpty();

    declarativeSampler.sample(traced(1.0f, true));

    assertThat(declarativeSampler.methodsToSamplers)
      .hasSize(1);

    declarativeSampler.sample(traced(0.0f, true));

    assertThat(declarativeSampler.methodsToSamplers)
      .hasSize(2);
  }

  @Test public void cardinalityIsPerAnnotationNotInvocation() {
    Traced traced = traced(1.0f, true);

    declarativeSampler.sample(traced);
    declarativeSampler.sample(traced);
    declarativeSampler.sample(traced);

    assertThat(declarativeSampler.methodsToSamplers)
      .hasSize(1);
  }

  @Retention(RetentionPolicy.RUNTIME) public @interface Traced {
    float sampleRate() default 1.0f;

    boolean enabled() default true;
  }

  static Traced traced(float sampleRate, boolean enabled) {
    return new Traced() {
      @Override public Class<? extends Annotation> annotationType() {
        return Traced.class;
      }

      @Override public float sampleRate() {
        return sampleRate;
      }

      @Override public boolean enabled() {
        return enabled;
      }
    };
  }
}
