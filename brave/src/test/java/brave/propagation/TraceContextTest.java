package brave.propagation;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextTest {

  @Test public void compareUnequalIds() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(0L).build();

    assertThat(context)
        .isNotEqualTo(TraceContext.newBuilder().traceId(333L).spanId(1L).build());
  }

  @Test public void compareEqualIds() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(444L).build();

    assertThat(context)
        .isEqualTo(TraceContext.newBuilder().traceId(333L).spanId(444L).build());
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
}
