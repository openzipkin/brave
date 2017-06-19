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
