/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdContextTest {
  TraceIdContext base = TraceIdContext.newBuilder().traceId(333L).build();

  @Test void canUsePrimitiveOverloads_true() {
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

  @Test void canUsePrimitiveOverloads_false() {
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

  @Test void canSetSampledNull() {
    base = base.toBuilder().sampled(true).build();

    TraceIdContext objects = base.toBuilder().sampled(null).build();

    assertThat(objects.debug())
      .isFalse();
    assertThat(objects.sampled())
      .isNull();
  }

  @Test void compareUnequalIds() {
    assertThat(base)
      .isNotEqualTo(base.toBuilder().traceIdHigh(222L).build());
  }

  @Test void compareEqualIds() {
    assertThat(base)
      .isEqualTo(TraceIdContext.newBuilder().traceId(333L).build());
  }

  @Test void testToString_lo() {
    assertThat(base.toString())
      .isEqualTo("000000000000014d");
  }

  @Test void testToString() {
    assertThat(base.toBuilder().traceIdHigh(222L).build().toString())
      .isEqualTo("00000000000000de000000000000014d");
  }
}
