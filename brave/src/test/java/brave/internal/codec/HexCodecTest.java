/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.internal.codec;

import org.junit.Test;

import static brave.internal.codec.HexCodec.lenientLowerHexToUnsignedLong;
import static brave.internal.codec.HexCodec.lowerHexToUnsignedLong;
import static brave.internal.codec.HexCodec.toLowerHex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

// code originally imported from zipkin.UtilTest
public class HexCodecTest {

  @Test
  public void lowerHexToUnsignedLong_downgrades128bitIdsByDroppingHighBits() {
    assertThat(lowerHexToUnsignedLong("463ac35c9f6413ad48485a3953bb6124"))
      .isEqualTo(lowerHexToUnsignedLong("48485a3953bb6124"));
  }

  /** This tests that the being index is inclusive and the end index is exclusive */
  @Test
  public void lenientLowerHexToUnsignedLong_ignoresBeforeAndAfter() {
    // intentionally shorter than 16 characters
    lenientLowerHexToUnsignedLong_ignoresBeforeAndAfter("12345678");
    // exactly 16 characters
    lenientLowerHexToUnsignedLong_ignoresBeforeAndAfter("1234567812345678");
  }

  void lenientLowerHexToUnsignedLong_ignoresBeforeAndAfter(String encoded) {
    String sequence = "??" + encoded + "??";
    assertThat(lenientLowerHexToUnsignedLong(sequence, 2, 2 + encoded.length()))
      .isEqualTo(lowerHexToUnsignedLong(encoded))
      .isEqualTo(Long.parseUnsignedLong(encoded, 16));
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
}
