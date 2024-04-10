/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal;

import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;

import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static brave.internal.InternalPropagation.sampled;
import static org.assertj.core.api.Assertions.assertThat;

class InternalPropagationTest {
  @Test void set_sampled_true() {
    assertThat(sampled(true, 0))
      .isEqualTo(FLAG_SAMPLED_SET + FLAG_SAMPLED);
  }

  @Test void set_sampled_false() {
    assertThat(sampled(false, FLAG_SAMPLED_SET | FLAG_SAMPLED))
      .isEqualTo(FLAG_SAMPLED_SET);
  }

  static {
    SamplingFlags.EMPTY.toString(); // ensure wired
  }

  @Test void shallowCopy() {
    TraceContext context = TraceContext.newBuilder().traceId(1).spanId(2).debug(true)
      .addExtra(1L).build();

    assertThat(InternalPropagation.instance.shallowCopy(context))
      .isNotSameAs(context)
      .usingRecursiveComparison()
      .isEqualTo(context);
  }
}
