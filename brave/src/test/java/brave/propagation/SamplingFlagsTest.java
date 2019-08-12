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
package brave.propagation;

import org.junit.Test;

import static brave.internal.InternalPropagation.FLAG_DEBUG;
import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_LOCAL;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static brave.propagation.SamplingFlags.toSamplingFlags;
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

  /** Ensures constants are used */
  @Test public void toSamplingFlags_returnsConstants() {
    assertThat(toSamplingFlags(SamplingFlags.EMPTY.flags))
      .isSameAs(SamplingFlags.EMPTY);
    assertThat(toSamplingFlags(SamplingFlags.NOT_SAMPLED.flags))
      .isSameAs(SamplingFlags.NOT_SAMPLED);
    assertThat(toSamplingFlags(SamplingFlags.SAMPLED.flags))
      .isSameAs(SamplingFlags.SAMPLED);
    assertThat(toSamplingFlags(SamplingFlags.DEBUG.flags))
      .isSameAs(SamplingFlags.DEBUG);
    assertThat(toSamplingFlags(SamplingFlags.EMPTY.flags | FLAG_SAMPLED_LOCAL))
      .isSameAs(SamplingFlags.EMPTY_SAMPLED_LOCAL);
    assertThat(toSamplingFlags(SamplingFlags.NOT_SAMPLED.flags | FLAG_SAMPLED_LOCAL))
      .isSameAs(SamplingFlags.NOT_SAMPLED_SAMPLED_LOCAL);
    assertThat(toSamplingFlags(SamplingFlags.SAMPLED.flags | FLAG_SAMPLED_LOCAL))
      .isSameAs(SamplingFlags.SAMPLED_SAMPLED_LOCAL);
    assertThat(toSamplingFlags(SamplingFlags.DEBUG.flags | FLAG_SAMPLED_LOCAL))
      .isSameAs(SamplingFlags.DEBUG_SAMPLED_LOCAL);
  }

  @Test public void sampledLocal() {
    SamplingFlags.Builder flagsBuilder = new SamplingFlags.Builder();
    flagsBuilder.flags |= FLAG_SAMPLED_LOCAL;
    SamplingFlags flags = flagsBuilder.build();

    assertThat(flags).isSameAs(SamplingFlags.EMPTY_SAMPLED_LOCAL);
    assertThat(flags.sampledLocal()).isTrue();
    assertThat(flags.sampled()).isNull();
  }
}
