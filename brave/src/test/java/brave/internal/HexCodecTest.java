package brave.internal;

import brave.propagation.TraceContext;
import org.junit.Test;

import static brave.internal.HexCodec.lowerHexEqualsTraceId;
import static brave.internal.HexCodec.lowerHexEqualsUnsignedLong;
import static brave.internal.HexCodec.lowerHexToUnsignedLong;
import static brave.internal.HexCodec.toLowerHex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

// code originally imported from zipkin.UtilTest
public class HexCodecTest {

  @Test
  public void lowerHexToUnsignedLong_downgrades128bitIdsByDroppingHighBits() {
    assertThat(lowerHexToUnsignedLong("463ac35c9f6413ad48485a3953bb6124"))
        .isEqualTo(lowerHexToUnsignedLong("48485a3953bb6124"));
  }

  @Test
  public void lowerHexToUnsignedLongTest() {
    assertThat(lowerHexToUnsignedLong("ffffffffffffffff")).isEqualTo(-1);
    assertThat(lowerHexToUnsignedLong(Long.toHexString(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);

    try {
      lowerHexToUnsignedLong("0"); // invalid
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {
    }

    try {
      lowerHexToUnsignedLong(Character.toString((char) ('9' + 1))); // invalid
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {
    }

    try {
      lowerHexToUnsignedLong(Character.toString((char) ('0' - 1))); // invalid
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {
    }

    try {
      lowerHexToUnsignedLong(Character.toString((char) ('f' + 1))); // invalid
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {
    }

    try {
      lowerHexToUnsignedLong(Character.toString((char) ('a' - 1))); // invalid
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {
    }

    try {
      lowerHexToUnsignedLong("fffffffffffffffffffffffffffffffff"); // too long
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {

    }

    try {
      lowerHexToUnsignedLong(""); // too short
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {

    }

    try {
      lowerHexToUnsignedLong("rs"); // bad charset
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {

    }
  }

  @Test
  public void toLowerHex_minValue() {
    assertThat(toLowerHex(Long.MAX_VALUE)).isEqualTo("7fffffffffffffff");
  }

  @Test
  public void toLowerHex_midValue() {
    assertThat(toLowerHex(3405691582L)).isEqualTo("00000000cafebabe");
  }

  @Test
  public void toLowerHex_fixedLength() {
    assertThat(toLowerHex(0L)).isEqualTo("0000000000000000");
  }

  @Test public void toLowerHex_whenNotHigh_16Chars() {
    assertThat(toLowerHex(0L, 12345678L))
        .hasToString("0000000000bc614e");
  }

  @Test public void toLowerHex_whenHigh_32Chars() {
    assertThat(toLowerHex(1234L, 5678L))
        .hasToString("00000000000004d2000000000000162e");
  }

  @Test public void lowerHexEqualsUnsignedLong_minValue() {
    assertThat(lowerHexEqualsUnsignedLong("7fffffffffffffff", Long.MAX_VALUE))
        .isTrue();
  }

  @Test public void lowerHexEqualsUnsignedLong_midValue() {
    assertThat(lowerHexEqualsUnsignedLong("00000000cafebabe", 3405691582L))
        .isTrue();
  }

  @Test public void lowerHexEqualsUnsignedLong_whenNotHigh_16Chars() {
    TraceContext context = TraceContext.newBuilder()
        .traceId(12345678L)
        .spanId(1L)
        .build();
    assertThat(lowerHexEqualsTraceId("0000000000bc614e", context))
        .isTrue();
    assertThat(lowerHexEqualsTraceId("00000000000000000000000000bc614e", context))
        .isTrue();
  }

  @Test public void lowerHexEqualsUnsignedLong_paddedTraceIdOk() {
    TraceContext context = TraceContext.newBuilder()
        .traceId(12345678L)
        .spanId(1L)
        .build();
    assertThat(lowerHexEqualsTraceId("00000000000000000000000000bc614e", context))
        .isTrue();
  }

  @Test public void lowerHexEqualsUnsignedLong_whenHigh_32Chars() {
    TraceContext context = TraceContext.newBuilder()
        .traceIdHigh(1234L)
        .traceId(5678L)
        .spanId(1L)
        .build();

    assertThat(lowerHexEqualsTraceId("000000000000162e", context))
        .isFalse();
    assertThat(lowerHexEqualsTraceId("00000000000004d2000000000000162e", context))
        .isTrue();
  }
}
