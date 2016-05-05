package com.github.kristofa.brave;

import com.twitter.finagle.tracing.TraceId;
import com.twitter.finagle.tracing.TraceId$;
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

    assertThat(SpanId.fromBytes(finagle.serialize(finagle)))
        .isEqualTo(brave);
  }
}
