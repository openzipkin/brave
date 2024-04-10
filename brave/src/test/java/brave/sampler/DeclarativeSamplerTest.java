/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.sampler;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static brave.sampler.DeclarativeSampler.NULL_SENTINEL;
import static org.assertj.core.api.Assertions.assertThat;

class DeclarativeSamplerTest {

  DeclarativeSampler<Traced> declarativeSampler =
    DeclarativeSampler.createWithProbability(t -> t.enabled() ? t.sampleProbability() : null);

  @BeforeEach void clear() {
    declarativeSampler.methodToSamplers.clear();
  }

  @Test void honorsSampleRate() {
    declarativeSampler = DeclarativeSampler.createWithRate(Traced::sampleRate);

    assertThat(declarativeSampler.trySample(traced(0.0f, 1, true)))
      .isTrue();

    assertThat(declarativeSampler.trySample(traced(0.0f, 0, true)))
      .isFalse();
  }

  @Test void honorsSampleProbability() {
    declarativeSampler = DeclarativeSampler.createWithProbability(Traced::sampleProbability);

    assertThat(declarativeSampler.trySample(traced(1.0f, 0, true)))
      .isTrue();

    assertThat(declarativeSampler.trySample(traced(0.0f, 0, true)))
      .isFalse();
  }

  @Test void nullOnNull() {
    assertThat(declarativeSampler.trySample(null))
      .isNull();
  }

  @Test void unmatched() {
    DeclarativeSampler<Object> declarativeSampler = DeclarativeSampler.createWithRate(o -> null);

    assertThat(declarativeSampler.trySample(new Object()))
      .isNull();

    // this decision is cached
    assertThat(declarativeSampler.methodToSamplers)
      .containsValue(NULL_SENTINEL);
  }

  @Test void acceptsFallback() {
    assertThat(declarativeSampler.trySample(traced(1.0f, 0, false)))
      .isNull();
  }

  @Test void samplerLoadsLazy() {
    assertThat(declarativeSampler.methodToSamplers)
      .isEmpty();

    declarativeSampler.trySample(traced(1.0f, 0, true));

    assertThat(declarativeSampler.methodToSamplers)
      .hasSize(1);

    declarativeSampler.trySample(traced(0.0f, 0, true));

    assertThat(declarativeSampler.methodToSamplers)
      .hasSize(2);
  }

  @Test void cardinalityIsPerAnnotationNotInvocation() {
    Traced traced = traced(1.0f, 0, true);

    declarativeSampler.trySample(traced);
    declarativeSampler.trySample(traced);
    declarativeSampler.trySample(traced);

    assertThat(declarativeSampler.methodToSamplers)
      .hasSize(1);
  }

  @Retention(RetentionPolicy.RUNTIME) public @interface Traced {
    float sampleProbability() default 1.0f;

    int sampleRate() default 0;

    boolean enabled() default true;
  }

  static Traced traced(float sampleProbability, int sampleRate, boolean enabled) {
    return new Traced() {
      @Override public Class<? extends Annotation> annotationType() {
        return Traced.class;
      }

      @Override public float sampleProbability() {
        return sampleProbability;
      }

      @Override public int sampleRate() {
        return sampleRate;
      }

      @Override public boolean enabled() {
        return enabled;
      }
    };
  }
}
