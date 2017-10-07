package brave.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceIdContextTest {
  TraceIdContext context = TraceIdContext.newBuilder().traceId(333L).build();

  @Test public void compareUnequalIds() {
    assertThat(context)
        .isNotEqualTo(context.toBuilder().traceIdHigh(222L).build());
  }

  @Test public void compareEqualIds() {
    assertThat(context)
        .isEqualTo(TraceIdContext.newBuilder().traceId(333L).build());
  }

  @Test public void testToString_lo() {
    assertThat(context.toString())
        .isEqualTo("000000000000014d");
  }

  @Test public void testToString() {
    assertThat(context.toBuilder().traceIdHigh(222L).build().toString())
        .isEqualTo("00000000000000de000000000000014d");
  }
}
