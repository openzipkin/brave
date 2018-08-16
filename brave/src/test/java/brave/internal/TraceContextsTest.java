package brave.internal;

import brave.propagation.TraceContext;
import java.util.Arrays;
import org.junit.Test;

import static brave.internal.TraceContexts.FLAG_SAMPLED;
import static brave.internal.TraceContexts.FLAG_SAMPLED_SET;
import static brave.internal.TraceContexts.contextWithExtra;
import static brave.internal.TraceContexts.sampled;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextsTest {
  TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();

  @Test public void contextWithExtra_notEmpty() {
    assertThat(contextWithExtra(context, Arrays.asList(1L)))
        .extracting("extra")
        .containsExactly(Arrays.asList(1L));
  }

  @Test public void contextWithExtra_empty() {
    assertThat(contextWithExtra(context.toBuilder().extra(Arrays.asList(1L)).build(), emptyList()))
        .extracting("extra")
        .containsExactly(emptyList());
  }

  @Test public void get_sampled_true_object() {
    assertThat(sampled(Boolean.TRUE, 0))
        .isEqualTo(FLAG_SAMPLED_SET | FLAG_SAMPLED);
  }

  @Test public void get_sampled_false_object() {
    assertThat(sampled(Boolean.FALSE, FLAG_SAMPLED_SET | FLAG_SAMPLED))
        .isEqualTo(FLAG_SAMPLED_SET);
  }

  @Test public void set_sampled_true_object() {
    assertThat(sampled(Boolean.TRUE, 0))
        .isEqualTo(FLAG_SAMPLED_SET + FLAG_SAMPLED);
  }

  @Test public void set_sampled_false_object() {
    assertThat(sampled(Boolean.FALSE, FLAG_SAMPLED_SET | FLAG_SAMPLED))
        .isEqualTo(FLAG_SAMPLED_SET);
  }

  @Test public void set_sampled_true() {
    assertThat(sampled(true, 0))
        .isEqualTo(FLAG_SAMPLED_SET + FLAG_SAMPLED);
  }

  @Test public void set_sampled_false() {
    assertThat(sampled(false, FLAG_SAMPLED_SET | FLAG_SAMPLED))
        .isEqualTo(FLAG_SAMPLED_SET);
  }
}
