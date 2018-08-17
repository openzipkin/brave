package brave.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextTest {
  TraceContext base = TraceContext.newBuilder().traceId(1L).spanId(1L).build();

  @Test public void compareUnequalIds() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3L).build();

    assertThat(context)
        .isNotEqualTo(TraceContext.newBuilder().traceId(333L).spanId(1L).build());
    assertThat(context.hashCode())
        .isNotEqualTo(TraceContext.newBuilder().traceId(333L).spanId(1L).build().hashCode());
  }

  /**
   * Shared context is different than an unshared one, notably this keeps client/server loopback
   * separate.
   */
  @Test public void compareUnequalIds_onShared() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3L).build();

    assertThat(context)
        .isNotEqualTo(context.toBuilder().shared(true).build());
    assertThat(context.hashCode())
        .isNotEqualTo(context.toBuilder().shared(true).build().hashCode());
  }

  @Test public void compareEqualIds() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(444L).build();

    assertThat(context)
        .isEqualTo(TraceContext.newBuilder().traceId(333L).spanId(444L).build());
    assertThat(context.hashCode())
        .isEqualTo(TraceContext.newBuilder().traceId(333L).spanId(444L).build().hashCode());
  }

  @Test public void equalOnSameTraceIdSpanId() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(444L).build();

    assertThat(context)
        .isEqualTo(context.toBuilder().parentId(1L).build());
    assertThat(context.hashCode())
        .isEqualTo(context.toBuilder().parentId(1L).build().hashCode());
  }

  @Test
  public void testToString_lo() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3).parentId(2L).build();

    assertThat(context.toString())
        .isEqualTo("000000000000014d/0000000000000003");
  }

  @Test
  public void testToString() {
    TraceContext context =
        TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).parentId(2L).build();

    assertThat(context.toString())
        .isEqualTo("000000000000014d00000000000001bc/0000000000000003");
  }

  @Test public void canUsePrimitiveOverloads() {
    TraceContext primitives = base.toBuilder()
        .parentId(1L)
        .sampled(true)
        .debug(true)
        .build();

    TraceContext objects = base.toBuilder()
        .parentId(Long.valueOf(1L))
        .sampled(Boolean.TRUE)
        .debug(Boolean.TRUE)
        .build();

    assertThat(primitives)
        .isEqualToComparingFieldByField(objects);
  }

  @Test public void nullToZero() {
    TraceContext nulls = base.toBuilder()
        .parentId(null)
        .build();

    TraceContext zeros = base.toBuilder()
        .parentId(0L)
        .build();

    assertThat(nulls)
        .isEqualToComparingFieldByField(zeros);
  }
}
