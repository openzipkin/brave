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

import static org.assertj.core.api.Assertions.assertThat;

public class TraceIdContextTest {
  TraceIdContext base = TraceIdContext.newBuilder().traceId(333L).build();

  @Test public void canUsePrimitiveOverloads_true() {
    TraceIdContext primitives = base.toBuilder()
      .sampled(true)
      .debug(true)
      .build();

    TraceIdContext objects = base.toBuilder()
      .sampled(Boolean.TRUE)
      .debug(Boolean.TRUE)
      .build();

    assertThat(primitives)
      .isEqualToComparingFieldByField(objects);
    assertThat(primitives.debug())
      .isTrue();
    assertThat(primitives.sampled())
      .isTrue();
  }

  @Test public void canUsePrimitiveOverloads_false() {
    base = base.toBuilder().debug(true).build();

    TraceIdContext primitives = base.toBuilder()
      .sampled(false)
      .debug(false)
      .build();

    TraceIdContext objects = base.toBuilder()
      .sampled(Boolean.FALSE)
      .debug(Boolean.FALSE)
      .build();

    assertThat(primitives)
      .isEqualToComparingFieldByField(objects);
    assertThat(primitives.debug())
      .isFalse();
    assertThat(primitives.sampled())
      .isFalse();
  }

  @Test public void canSetSampledNull() {
    base = base.toBuilder().sampled(true).build();

    TraceIdContext objects = base.toBuilder().sampled(null).build();

    assertThat(objects.debug())
      .isFalse();
    assertThat(objects.sampled())
      .isNull();
  }

  @Test public void compareUnequalIds() {
    assertThat(base)
      .isNotEqualTo(base.toBuilder().traceIdHigh(222L).build());
  }

  @Test public void compareEqualIds() {
    assertThat(base)
      .isEqualTo(TraceIdContext.newBuilder().traceId(333L).build());
  }

  @Test public void testToString_lo() {
    assertThat(base.toString())
      .isEqualTo("000000000000014d");
  }

  @Test public void testToString() {
    assertThat(base.toBuilder().traceIdHigh(222L).build().toString())
      .isEqualTo("00000000000000de000000000000014d");
  }
}
