package brave.internal;

import org.junit.Test;

import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static brave.internal.InternalPropagation.sampled;
import static org.assertj.core.api.Assertions.assertThat;

public class InternalPropagationTest {

  @Test public void set_sampled_true() {
    assertThat(sampled(true, 0))
        .isEqualTo(FLAG_SAMPLED_SET + FLAG_SAMPLED);
  }

  @Test public void set_sampled_false() {
    assertThat(sampled(false, FLAG_SAMPLED_SET | FLAG_SAMPLED))
        .isEqualTo(FLAG_SAMPLED_SET);
  }
}
