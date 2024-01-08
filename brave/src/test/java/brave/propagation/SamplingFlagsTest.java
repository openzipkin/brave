/*
 * Copyright 2013-2024 The OpenZipkin Authors
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

import org.junit.jupiter.api.Test;

import static brave.internal.InternalPropagation.FLAG_DEBUG;
import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_LOCAL;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;
import static brave.propagation.SamplingFlags.toSamplingFlags;
import static org.assertj.core.api.Assertions.assertThat;

class SamplingFlagsTest {
  @Test void debug_set_true() {
    assertThat(SamplingFlags.debug(true, SamplingFlags.EMPTY.flags))
      .isEqualTo(SamplingFlags.DEBUG.flags)
      .isEqualTo(FLAG_SAMPLED_SET | FLAG_SAMPLED | FLAG_DEBUG);
  }

  @Test void sampled_flags() {
    assertThat(SamplingFlags.debug(false, SamplingFlags.DEBUG.flags))
      .isEqualTo(SamplingFlags.SAMPLED.flags)
      .isEqualTo(FLAG_SAMPLED_SET | FLAG_SAMPLED);
  }

  /** Ensures constants are used */
  @Test void toSamplingFlags_returnsConstantsAndHasNiceToString() {
    assertThat(toSamplingFlags(SamplingFlags.EMPTY.flags))
      .isSameAs(SamplingFlags.EMPTY)
      .hasToString("");
    assertThat(toSamplingFlags(SamplingFlags.NOT_SAMPLED.flags))
      .isSameAs(SamplingFlags.NOT_SAMPLED)
      .hasToString("NOT_SAMPLED_REMOTE");
    assertThat(toSamplingFlags(SamplingFlags.SAMPLED.flags))
      .isSameAs(SamplingFlags.SAMPLED)
      .hasToString("SAMPLED_REMOTE");
    assertThat(toSamplingFlags(SamplingFlags.DEBUG.flags))
      .isSameAs(SamplingFlags.DEBUG)
      .hasToString("DEBUG");
    assertThat(toSamplingFlags(SamplingFlags.EMPTY.flags | FLAG_SAMPLED_LOCAL))
      .isSameAs(SamplingFlags.EMPTY_SAMPLED_LOCAL)
      .hasToString("SAMPLED_LOCAL");
    assertThat(toSamplingFlags(SamplingFlags.NOT_SAMPLED.flags | FLAG_SAMPLED_LOCAL))
      .isSameAs(SamplingFlags.NOT_SAMPLED_SAMPLED_LOCAL)
      .hasToString("NOT_SAMPLED_REMOTE|SAMPLED_LOCAL");
    assertThat(toSamplingFlags(SamplingFlags.SAMPLED.flags | FLAG_SAMPLED_LOCAL))
      .isSameAs(SamplingFlags.SAMPLED_SAMPLED_LOCAL)
      .hasToString("SAMPLED_REMOTE|SAMPLED_LOCAL");
    assertThat(toSamplingFlags(SamplingFlags.DEBUG.flags | FLAG_SAMPLED_LOCAL))
      .isSameAs(SamplingFlags.DEBUG_SAMPLED_LOCAL)
      .hasToString("DEBUG|SAMPLED_LOCAL");
  }
}
