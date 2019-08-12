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
