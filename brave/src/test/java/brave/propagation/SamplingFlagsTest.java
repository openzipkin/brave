package brave.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SamplingFlagsTest {

  @Test public void create_sampledNullDebugFalse() {
    SamplingFlags flags = SamplingFlags.create(null, false);

    assertThat(flags).isSameAs(SamplingFlags.EMPTY);
    assertThat(flags.sampled()).isNull();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void create_sampledNullDebugTrue() {
    SamplingFlags flags = SamplingFlags.create(null, true).build();

    assertThat(flags).isSameAs(SamplingFlags.DEBUG);
    assertThat(flags.sampled()).isTrue();
    assertThat(flags.debug()).isTrue();
  }

  @Test public void create_sampledTrueDebugFalse() {
    SamplingFlags flags = SamplingFlags.create(true, false).build();

    assertThat(flags).isSameAs(SamplingFlags.SAMPLED);
    assertThat(flags.sampled()).isTrue();
    assertThat(flags.debug()).isFalse();
  }

  @Test public void create_sampledFalseDebugFalse() {
    SamplingFlags flags = SamplingFlags.create(false, false).build();

    assertThat(flags).isSameAs(SamplingFlags.NOT_SAMPLED);
    assertThat(flags.sampled()).isFalse();
    assertThat(flags.debug()).isFalse();
  }

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
}
