package brave.sampler;

import brave.propagation.SamplingFlags;
import com.google.auto.value.AutoAnnotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DeclarativeSamplerTest {

  DeclarativeSampler<Traced> sampler =
      DeclarativeSampler.create(t -> t.enabled() ? t.sampleRate() : null);

  @Before public void clear() {
    sampler.methodsToSamplers.clear();
  }

  @Test public void honorsSampleRate() {
    assertThat(sampler.sample(traced(1.0f, true)))
        .isEqualTo(SamplingFlags.SAMPLED);

    assertThat(sampler.sample(traced(0.0f, true)))
        .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void acceptsFallback() {
    assertThat(sampler.sample(traced(1.0f, false)))
        .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void samplerLoadsLazy() {
    assertThat(sampler.methodsToSamplers)
        .isEmpty();

    sampler.sample(traced(1.0f, true));

    assertThat(sampler.methodsToSamplers)
        .hasSize(1);

    sampler.sample(traced(0.0f, true));

    assertThat(sampler.methodsToSamplers)
        .hasSize(2);
  }

  @Test public void cardinalityIsPerAnnotationNotInvocation() {
    Traced traced = traced(1.0f, true);

    sampler.sample(traced);
    sampler.sample(traced);
    sampler.sample(traced);

    assertThat(sampler.methodsToSamplers)
        .hasSize(1);
  }

  @Retention(RetentionPolicy.RUNTIME) public @interface Traced {
    float sampleRate() default 1.0f;

    boolean enabled() default true;
  }

  // note: Unlike normal java annotations, auto-annotation do value-based equals and hashCode
  @AutoAnnotation static Traced traced(float sampleRate, boolean enabled) {
    return new AutoAnnotation_DeclarativeSamplerTest_traced(sampleRate, enabled);
  }
}
