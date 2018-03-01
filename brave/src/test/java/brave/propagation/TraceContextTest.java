package brave.propagation;

import brave.internal.HexCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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

  @Test(expected = UnsupportedOperationException.class)
  public void ensureImmutable_returnsImmutableEmptyList() {
    TraceContext.ensureImmutable(new ArrayList<>()).add("foo");
  }

  @Test public void ensureImmutable_convertsToSingletonList() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    TraceContext.ensureImmutable(list);
    assertThat(TraceContext.ensureImmutable(list).getClass().getSimpleName())
        .isEqualTo("SingletonList");
  }

  @Test public void ensureImmutable_returnsEmptyList() {
    List<Object> list = Collections.emptyList();
    assertThat(TraceContext.ensureImmutable(list))
        .isSameAs(list);
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

  @Test public void parseSpanId() {
    String spanIdString = "48485a3953bb6124";

    TestBuilder builder = parseGoodSpanId(spanIdString);

    assertThat(HexCodec.toLowerHex(builder.spanId))
        .isEqualTo(spanIdString);
  }

  @Test public void parseSpanId_short64bit() {
    String spanIdString = "6124";

    TestBuilder builder = parseGoodSpanId(spanIdString);

    assertThat(HexCodec.toLowerHex(builder.spanId))
        .isEqualTo("000000000000" + spanIdString);
  }

  /**
   * Span ID is a required parameter, so it cannot be null empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test public void parseSpanId_malformedReturnsFalse() {
    parseBadSpanId("463acL$c9f6413ad");
    parseBadSpanId("holy 💩");
    parseBadSpanId("-");
    parseBadSpanId("");
    parseBadSpanId(null);

    assertThat(messages).containsExactly(
        "span-id: 463acL$c9f6413ad is not a lower-hex string",
        "span-id: holy 💩 is not a lower-hex string",
        "span-id should be a 1 to 16 character lower-hex string with no prefix",
        "span-id should be a 1 to 16 character lower-hex string with no prefix",
        "span-id was null"
    );
  }

  @Test public void parseSpanId_whenFineDisabledNoLogs() {
    logger.setLevel(Level.INFO);

    parseBadSpanId("463acL$c9f6413ad");
    parseBadSpanId("holy 💩");
    parseBadSpanId("-");
    parseBadSpanId("");
    parseBadSpanId(null);

    assertThat(messages).isEmpty();
  }

  TestBuilder parseGoodSpanId(String spanIdString) {
    TestBuilder builder = new TestBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseSpanId(getter, spanIdString, "span-id"))
        .isTrue();
    return builder;
  }

  void parseBadSpanId(String spanIdString) {
    TestBuilder builder = new TestBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseSpanId(getter, spanIdString, "span-id"))
        .isFalse();
    assertThat(builder.spanId).isZero();
  }

  @Test public void parseParentId() {
    String parentIdString = "48485a3953bb6124";

    TestBuilder builder = parseGoodParentId(parentIdString);

    assertThat(HexCodec.toLowerHex(builder.parentId))
        .isEqualTo(parentIdString);
  }

  @Test public void parseParentId_null_is_ok() {
    TestBuilder builder = parseGoodParentId(null);

    assertThat(builder.parentId).isZero();
  }

  @Test public void parseParentId_short64bit() {
    String parentIdString = "6124";

    TestBuilder builder = parseGoodParentId(parentIdString);

    assertThat(HexCodec.toLowerHex(builder.parentId))
        .isEqualTo("000000000000" + parentIdString);
  }

  /**
   * Parent Span ID is an optional parameter, but it cannot be empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test public void parseParentId_malformedReturnsFalse() {
    parseBadParentId("463acL$c9f6413ad");
    parseBadParentId("holy 💩");
    parseBadParentId("-");
    parseBadParentId("");

    assertThat(messages).containsExactly(
        "parent-id: 463acL$c9f6413ad is not a lower-hex string",
        "parent-id: holy 💩 is not a lower-hex string",
        "parent-id should be a 1 to 16 character lower-hex string with no prefix",
        "parent-id should be a 1 to 16 character lower-hex string with no prefix"
    );
  }

  @Test public void parseParentId_whenFineDisabledNoLogs() {
    logger.setLevel(Level.INFO);

    parseBadParentId("463acL$c9f6413ad");
    parseBadParentId("holy 💩");
    parseBadParentId("-");
    parseBadParentId("");

    assertThat(messages).isEmpty();
  }

  TestBuilder parseGoodParentId(String parentIdString) {
    TestBuilder builder = new TestBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseParentId(getter, parentIdString, "parent-id"))
        .isTrue();
    return builder;
  }

  void parseBadParentId(String parentIdString) {
    TestBuilder builder = new TestBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseParentId(getter, parentIdString, "parent-id"))
        .isFalse();
    assertThat(builder.parentId).isZero();
  }

  List<String> messages = new ArrayList<>();

  Logger logger = new Logger("", null) {
    {
      setLevel(Level.ALL);
    }

    @Override public void log(Level level, String msg) {
      assertThat(level).isEqualTo(Level.FINE);
      messages.add(msg);
    }
  };

  class TestBuilder extends TraceContext.InternalBuilder {
    @Override Logger logger() {
      return logger;
    }
  }
}
