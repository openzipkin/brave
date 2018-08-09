package brave.propagation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class SamplingFlagsTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test public void defaultIsEmpty() {
    SamplingFlags flags = new SamplingFlags.Builder().build();

    assertThat(flags).isSameAs(SamplingFlags.EMPTY);
    assertThat(flags.sampled()).isNull();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void debugImpliesSampled() {
    SamplingFlags flags = new SamplingFlags.Builder().debug(true).build();

    assertThat(flags).isSameAs(SamplingFlags.DEBUG);
    assertThat(flags.sampled()).isTrue();
    assertThat(flags.debug()).isTrue();
  }

  @Test public void sampled() {
    SamplingFlags flags = new SamplingFlags.Builder().sampled(true).build();

    assertThat(flags).isSameAs(SamplingFlags.SAMPLED);
    assertThat(flags.sampled()).isTrue();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void notSampled() {
    SamplingFlags flags = new SamplingFlags.Builder().sampled(false).build();

    assertThat(flags).isSameAs(SamplingFlags.NOT_SAMPLED);
    assertThat(flags.sampled()).isFalse();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void nullSampled() {
    SamplingFlags flags = new SamplingFlags.Builder().sampled(true).sampled(null).build();

    assertThat(flags).isSameAs(SamplingFlags.EMPTY);
    assertThat(flags.sampled()).isNull();
    assertThat(flags.debug()).isFalse();
  }
}
