package brave.propagation;

import brave.internal.HexCodec;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InternalMutableTraceContextTest {

  @Test(expected = UnsupportedOperationException.class)
  public void ensureImmutable_returnsImmutableEmptyList() {
    InternalMutableTraceContext.ensureImmutable(new ArrayList<>()).add("foo");
  }

  @Test public void ensureImmutable_convertsToSingletonList() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    assertThat(InternalMutableTraceContext.ensureImmutable(list).getClass().getSimpleName())
        .isEqualTo("SingletonList");
  }

  @Test public void ensureImmutable_returnsEmptyList() {
    List<Object> list = Collections.emptyList();
    assertThat(InternalMutableTraceContext.ensureImmutable(list))
        .isSameAs(list);
  }

  @Test public void ensureImmutable_doesntCopySingletonList() {
    List<Object> list = Collections.singletonList("foo");
    assertThat(InternalMutableTraceContext.ensureImmutable(list))
        .isSameAs(list);
  }

  @Test public void ensureImmutable_doesntCopyUnmodifiableList() {
    List<Object> list = Collections.unmodifiableList(Arrays.asList("foo"));
    assertThat(InternalMutableTraceContext.ensureImmutable(list))
        .isSameAs(list);
  }

  @Test public void ensureImmutable_doesntCopyImmutableList() {
    List<Object> list = ImmutableList.of("foo");
    assertThat(InternalMutableTraceContext.ensureImmutable(list))
        .isSameAs(list);
  }

  @Test public void debugImpliesSampled() {
    MutableTraceContext primitives = new MutableTraceContext();
    primitives._debug(true);

    MutableTraceContext objects = new MutableTraceContext();
    objects._sampled(Boolean.TRUE);
    objects._debug(Boolean.TRUE);

    assertThat(primitives)
        .isEqualToComparingFieldByField(objects);
  }

  @Test public void canUsePrimitiveOverloads() {
    MutableTraceContext primitives = new MutableTraceContext();
    primitives._sampled(true);

    MutableTraceContext objects = new MutableTraceContext();
    primitives._sampled(Boolean.TRUE);

    assertThat(primitives)
        .isEqualToComparingFieldByField(objects);
  }

  @Test public void parseTraceId_128bit() {
    String traceIdString = "463ac35c9f6413ad48485a3953bb6124";

    MutableTraceContext builder = parseGoodTraceID(traceIdString);

    assertThat(HexCodec.toLowerHex(builder.traceIdHigh))
        .isEqualTo("463ac35c9f6413ad");
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo("48485a3953bb6124");
  }

  @Test public void parseTraceId_64bit() {
    String traceIdString = "48485a3953bb6124";

    MutableTraceContext builder = parseGoodTraceID(traceIdString);

    assertThat(builder.traceIdHigh).isZero();
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo(traceIdString);
  }

  @Test public void parseTraceId_short128bit() {
    String traceIdString = "3ac35c9f6413ad48485a3953bb6124";

    MutableTraceContext builder = parseGoodTraceID(traceIdString);

    assertThat(HexCodec.toLowerHex(builder.traceIdHigh))
        .isEqualTo("003ac35c9f6413ad");
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo("48485a3953bb6124");
  }

  @Test public void parseTraceId_short64bit() {
    String traceIdString = "6124";

    MutableTraceContext builder = parseGoodTraceID(traceIdString);

    assertThat(builder.traceIdHigh).isZero();
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo("000000000000" + traceIdString);
  }

  /**
   * Trace ID is a required parameter, so it cannot be null empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test public void parseTraceId_malformedReturnsFalse() {
    parseBadTraceId("463acL$c9f6413ad48485a3953bb6124");
    parseBadTraceId("holy 💩");
    parseBadTraceId("-");
    parseBadTraceId("");
    parseBadTraceId(null);

    assertThat(messages).containsExactly(
        "trace-id: 463acL$c9f6413ad48485a3953bb6124 is not a lower-hex string",
        "trace-id: holy 💩 is not a lower-hex string",
        "trace-id should be a 1 to 32 character lower-hex string with no prefix",
        "trace-id should be a 1 to 32 character lower-hex string with no prefix",
        "trace-id was null"
    );
  }

  @Test public void parseTraceId_whenFineDisabledNoLogs() {
    logger.setLevel(Level.INFO);

    parseBadTraceId("463acL$c9f6413ad48485a3953bb6124");
    parseBadTraceId("holy 💩");
    parseBadTraceId("-");
    parseBadTraceId("");
    parseBadTraceId(null);

    assertThat(messages).isEmpty();
  }

  @Test public void parseSpanId() {
    String spanIdString = "48485a3953bb6124";

    MutableTraceContext builder = parseGoodSpanId(spanIdString);

    assertThat(HexCodec.toLowerHex(builder.spanId))
        .isEqualTo(spanIdString);
  }

  @Test public void parseSpanId_short64bit() {
    String spanIdString = "6124";

    MutableTraceContext builder = parseGoodSpanId(spanIdString);

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

  MutableTraceContext parseGoodSpanId(String spanIdString) {
    MutableTraceContext builder = new MutableTraceContext();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseSpanId(getter, spanIdString, "span-id"))
        .isTrue();
    return builder;
  }

  void parseBadSpanId(String spanIdString) {
    MutableTraceContext builder = new MutableTraceContext();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseSpanId(getter, spanIdString, "span-id"))
        .isFalse();
    assertThat(builder.spanId).isZero();
  }

  @Test public void parseParentId() {
    String parentIdString = "48485a3953bb6124";

    MutableTraceContext builder = parseGoodParentId(parentIdString);

    assertThat(HexCodec.toLowerHex(builder.parentId))
        .isEqualTo(parentIdString);
  }

  @Test public void parseParentId_null_is_ok() {
    MutableTraceContext builder = parseGoodParentId(null);

    assertThat(builder.parentId).isZero();
  }

  @Test public void parseParentId_short64bit() {
    String parentIdString = "6124";

    MutableTraceContext builder = parseGoodParentId(parentIdString);

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

  MutableTraceContext parseGoodParentId(String parentIdString) {
    MutableTraceContext builder = new MutableTraceContext();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseParentId(getter, parentIdString, "parent-id"))
        .isTrue();
    return builder;
  }

  void parseBadParentId(String parentIdString) {
    MutableTraceContext builder = new MutableTraceContext();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseParentId(getter, parentIdString, "parent-id"))
        .isFalse();
    assertThat(builder.parentId).isZero();
  }

  MutableTraceContext parseGoodTraceID(String traceIdString) {
    MutableTraceContext builder = new MutableTraceContext();
    assertThat(builder.parseTraceId(traceIdString, "trace-id"))
        .isTrue();
    return builder;
  }

  void parseBadTraceId(String traceIdString) {
    MutableTraceContext builder = new MutableTraceContext();
    assertThat(builder.parseTraceId(traceIdString, "trace-id"))
        .isFalse();
    assertThat(builder.traceIdHigh).isZero();
    assertThat(builder.traceId).isZero();
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

  class MutableTraceContext extends InternalMutableTraceContext {
    @Override Logger logger() {
      return logger;
    }
  }
}
