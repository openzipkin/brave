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
