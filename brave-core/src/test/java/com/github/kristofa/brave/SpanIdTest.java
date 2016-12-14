package com.github.kristofa.brave;

import com.twitter.finagle.tracing.TraceId;
import com.twitter.finagle.tracing.TraceId$;
import com.twitter.zipkin.gen.Span;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class SpanIdTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test public void rootSpan_whenTraceIdsAreSpanIds() {
    SpanId id = SpanId.builder().spanId(333L).build();

    assertThat(id.root()).isTrue();
    assertThat(id.nullableParentId()).isNull();

    assertThat(SpanId.fromBytes(id.bytes()))
        .isEqualToComparingFieldByField(id);

    checkAgainstFinagle(id);
  }

  @Test public void equals() {
    assertThat(SpanId.builder().spanId(333L).build())
        .isEqualTo(SpanId.builder().spanId(333L).build());
  }

  // NOTE: finagle doesn't support this, but then again it doesn't provision non-span trace ids
  @Test public void rootSpan_whenTraceIdsArentSpanIds() {
    SpanId id = SpanId.builder().traceId(555L).parentId(null).spanId(333L).build();

    assertThat(id.root()).isTrue();
    assertThat(id.nullableParentId()).isNull();

    assertThat(SpanId.fromBytes(id.bytes()))
        .isEqualToComparingFieldByField(id);
  }

  @Test public void compareUnequalIds() {
    SpanId id = SpanId.builder().spanId(0L).build();

    assertThat(id)
        .isNotEqualTo(SpanId.builder().spanId(1L).build());

    checkAgainstFinagle(id);
  }

  @Test public void compareEqualIds() {
    SpanId id = SpanId.builder().spanId(0L).build();

    assertThat(id)
        .isEqualTo(SpanId.builder().spanId(0L).build());

    checkAgainstFinagle(id);
  }

  @Test public void compareSynthesizedParentId() {
    SpanId id = SpanId.builder().parentId(1L).spanId(1L).build();

    assertThat(id)
        .isEqualTo(SpanId.builder().spanId(1L).build());

    checkAgainstFinagle(id);
  }

  @Test public void compareSynthesizedTraceId() {
    SpanId id = SpanId.builder().traceId(1L).parentId(1L).spanId(1L).build();

    assertThat(id)
        .isEqualTo(SpanId.builder().parentId(1L).spanId(1L).build());

    checkAgainstFinagle(id);
  }

  @Test public void serializationRoundTrip() {
    SpanId id = SpanId.builder().traceId(1L).parentId(2L).spanId(3L).sampled(true).build();

    assertThat(SpanId.fromBytes(id.bytes()))
        .isEqualToComparingFieldByField(id);

    checkAgainstFinagle(id);
  }

  @Test public void fromBytesFail() {
    thrown.expect(IllegalArgumentException.class);

    SpanId.fromBytes("not-a-trace".getBytes());
  }

  @Test public void sampledTrueWhenDebug() {
    SpanId id = SpanId.builder().spanId(1L).debug(true).build();

    assertThat(id.sampled()).isTrue();

    checkAgainstFinagle(id);
  }

  @Test public void builderClearsSampled() {
    SpanId id = new SpanId(1L, 1L, 1L, SpanId.FLAG_SAMPLED | SpanId.FLAG_SAMPLING_SET);

    assertThat(id.sampled()).isTrue();

    checkAgainstFinagle(id);

    id = id.toBuilder().sampled(null).build();

    assertThat(id.sampled()).isNull();

    checkAgainstFinagle(id);
  }

  @Test public void builderUnsetsDebug() {
    SpanId id = new SpanId(1L, 1L, 1L, SpanId.FLAG_DEBUG);

    assertThat(id.debug()).isTrue();

    checkAgainstFinagle(id);

    id = id.toBuilder().debug(false).build();

    assertThat(id.debug()).isFalse();

    checkAgainstFinagle(id);
  }

  @Test public void equalsOnlyAccountsForIdFields() {
    assertThat(new SpanId(1L, 1L, 1L, SpanId.FLAG_DEBUG).hashCode())
        .isEqualTo(new SpanId(1L, 1L, 1L, SpanId.FLAG_SAMPLING_SET).hashCode());
  }

  @Test public void hashCodeOnlyAccountsForIdFields() {
    assertThat(new SpanId(1L, 1L, 1L, SpanId.FLAG_DEBUG))
        .isEqualTo(new SpanId(1L, 1L, 1L, SpanId.FLAG_SAMPLING_SET));
  }

  @Test
  public void testToString() {
    SpanId id = SpanId.builder().traceId(1).spanId(3).parentId(2L).build();

    assertThat(id.toString())
        .isEqualTo("0000000000000001.0000000000000003<:0000000000000002")
        .isEqualTo(TraceId$.MODULE$.deserialize(id.bytes()).get().toString());
  }

  @Test
  public void testToStringNullParent() {
    SpanId id = SpanId.builder().traceId(1).spanId(1).build();

    assertThat(id.toString())
        .isEqualTo("0000000000000001.0000000000000001<:0000000000000001")
        .isEqualTo(TraceId$.MODULE$.deserialize(id.bytes()).get().toString());
  }

  @Test
  public void testToString_hi() {
    SpanId id = SpanId.builder().traceIdHigh(1).traceId(2).spanId(3).parentId(2L).build();

    assertThat(id.toString())
        .isEqualTo("00000000000000010000000000000002.0000000000000003<:0000000000000002");
  }

  @Test
  public void testToStringNullParent_hi() {
    SpanId id = SpanId.builder().traceIdHigh(1).traceId(2).spanId(1).build();

    assertThat(id.toString())
        .isEqualTo("00000000000000010000000000000002.0000000000000001<:0000000000000002");
  }

  // TODO: update finagle to 128-bit and check accordingly
  // https://github.com/twitter/finagle/issues/564
  static void checkAgainstFinagle(SpanId brave) {
    TraceId finagle = TraceId$.MODULE$.deserialize(brave.bytes()).get();

    assertThat(finagle.traceId())
        .isEqualTo(brave.traceId);
    assertThat(finagle.parentId())
        .isEqualTo(brave.parentId);
    assertThat(finagle.spanId())
        .isEqualTo(brave.spanId);
    assertThat(finagle.flags().flags())
        .isEqualTo(brave.flags);

    assertThat(SpanId.fromBytes(TraceId.serialize(finagle)))
        .isEqualTo(brave);
  }

  @Test
  public void traceIdString() {
    SpanId id = SpanId.builder().traceId(1).spanId(1).build();

    assertThat(id.traceIdString())
        .isEqualTo("0000000000000001");
  }

  @Test
  public void traceIdString_128() {
    SpanId id = SpanId.builder().traceIdHigh(1).traceId(2).spanId(3).parentId(2L).build();

    assertThat(id.traceIdString())
        .isEqualTo("00000000000000010000000000000002");
  }

  @Test
  public void serializeRoundTrip_128() {
    SpanId id = SpanId.builder().traceIdHigh(1).traceId(2).spanId(3).parentId(2L).build();

    assertThat(SpanId.fromBytes(id.bytes()))
        .isEqualTo(id);
  }

  @Test
  public void creatingAChildInvalidatesShared() {
    SpanId id = SpanId.builder().traceIdHigh(1).traceId(2).spanId(3).parentId(2L).build();

    assertThat(SpanId.fromBytes(id.bytes()))
        .isEqualTo(id);
  }

  @Test
  public void toSpan_128() {
    SpanId id = SpanId.builder().traceIdHigh(1).traceId(2).spanId(3).parentId(2L).build();

    Span span = Span.fromSpanId(id);
    assertThat(span.getTrace_id_high()).isEqualTo(id.traceIdHigh);
    assertThat(span.getTrace_id()).isEqualTo(id.traceId);
    assertThat(span.getId()).isEqualTo(id.spanId);
    assertThat(span.getParent_id()).isEqualTo(id.parentId);
  }
}
