package brave.propagation;

import org.junit.Test;

import static brave.internal.TraceContexts.FLAG_DEBUG;
import static brave.internal.TraceContexts.FLAG_SAMPLED;
import static brave.internal.TraceContexts.FLAG_SAMPLED_SET;
import static org.assertj.core.api.Assertions.assertThat;

public class SamplingFlagsTest {

  @Test public void builder_defaultIsEmpty() {
    SamplingFlags flags = new SamplingFlags.Builder().build();

    assertThat(flags).isSameAs(SamplingFlags.EMPTY);
    assertThat(flags.sampled()).isNull();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void builder_debugImpliesSampled() {
    SamplingFlags flags = new SamplingFlags.Builder().debug(true).build();

    assertThat(flags).isSameAs(SamplingFlags.DEBUG);
    assertThat(flags.sampled()).isTrue();
    assertThat(flags.debug()).isTrue();
  }

  @Test public void builder_sampled() {
    SamplingFlags flags = new SamplingFlags.Builder().sampled(true).build();

    assertThat(flags).isSameAs(SamplingFlags.SAMPLED);
    assertThat(flags.sampled()).isTrue();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void builder_notSampled() {
    SamplingFlags flags = new SamplingFlags.Builder().sampled(false).build();

    assertThat(flags).isSameAs(SamplingFlags.NOT_SAMPLED);
    assertThat(flags.sampled()).isFalse();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void builder_nullSampled() {
    SamplingFlags flags = new SamplingFlags.Builder().sampled(true).sampled(null).build();

    assertThat(flags).isSameAs(SamplingFlags.EMPTY);
    assertThat(flags.sampled()).isNull();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void debug_set_true() {
    assertThat(SamplingFlags.debug(true, SamplingFlags.EMPTY.flags))
        .isEqualTo(SamplingFlags.DEBUG.flags)
        .isEqualTo(FLAG_SAMPLED_SET | FLAG_SAMPLED | FLAG_DEBUG);
  }

  @Test public void sampled_flags() {
    assertThat(SamplingFlags.debug(false, SamplingFlags.DEBUG.flags))
        .isEqualTo(SamplingFlags.SAMPLED.flags)
        .isEqualTo(FLAG_SAMPLED_SET | FLAG_SAMPLED);
  }
}
