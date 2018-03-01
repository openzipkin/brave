package brave.propagation;

import brave.internal.HexCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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

  @Test public void canUsePrimitiveOverloads() {
    TraceIdContext primitives = context.toBuilder()
        .sampled(true)
        .debug(true)
        .build();

    TraceIdContext objects = context.toBuilder()
        .sampled(Boolean.TRUE)
        .debug(Boolean.TRUE)
        .build();

    assertThat(primitives)
        .isEqualToComparingFieldByField(objects);
  }

  @Test public void parseTraceId_128bit() {
    String traceIdString = "463ac35c9f6413ad48485a3953bb6124";

    TestBuilder builder = parseGoodTraceID(traceIdString);

    assertThat(HexCodec.toLowerHex(builder.traceIdHigh))
        .isEqualTo("463ac35c9f6413ad");
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo("48485a3953bb6124");
  }

  @Test public void parseTraceId_64bit() {
    String traceIdString = "48485a3953bb6124";

    TestBuilder builder = parseGoodTraceID(traceIdString);

    assertThat(builder.traceIdHigh).isZero();
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo(traceIdString);
  }

  @Test public void parseTraceId_short128bit() {
    String traceIdString = "3ac35c9f6413ad48485a3953bb6124";

    TestBuilder builder = parseGoodTraceID(traceIdString);

    assertThat(HexCodec.toLowerHex(builder.traceIdHigh))
        .isEqualTo("003ac35c9f6413ad");
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo("48485a3953bb6124");
  }

  @Test public void parseTraceId_short64bit() {
    String traceIdString = "6124";

    TestBuilder builder = parseGoodTraceID(traceIdString);

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

  TestBuilder parseGoodTraceID(String traceIdString) {
    TestBuilder builder = new TestBuilder();
    assertThat(builder.parseTraceId(traceIdString, "trace-id"))
        .isTrue();
    return builder;
  }

  void parseBadTraceId(String traceIdString) {
    TestBuilder builder = new TestBuilder();
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

  class TestBuilder extends TraceIdContext.InternalBuilder {
    @Override Logger logger() {
      return logger;
    }
  }
}
