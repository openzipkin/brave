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

import java.util.Arrays;
import org.junit.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

// Originally a subset of zipkin2.internal.WriteBuffer
public class WriteBufferTest {
  // Adapted from http://stackoverflow.com/questions/8511490/calculating-length-in-utf-8-of-java-string-without-actually-encoding-it
  @Test public void utf8SizeInBytes() {
    for (int codepoint = 0; codepoint <= 0x10FFFF; codepoint++) {
      if (codepoint == 0xD800) codepoint = 0xDFFF + 1; // skip surrogates
      if (Character.isDefined(codepoint)) {
        String test = new String(Character.toChars(codepoint));
        int expected = test.getBytes(UTF_8).length;
        int actual = WriteBuffer.utf8SizeInBytes(test);
        if (actual != expected) {
          throw new AssertionError(actual + " length != " + expected + " for " + codepoint);
        }
      }
    }
  }

  /** Uses test data and codepoint wrapping trick from okhttp3.FormBodyTest */
  @Test public void utf8_malformed() {
    for (int codepoint : Arrays.asList(0xD800, 0xDFFF, 0xD83D)) {
      String test = new String(new int[] {'a', codepoint, 'c'}, 0, 3);
      assertThat(WriteBuffer.utf8SizeInBytes(test))
          .isEqualTo(3);

      byte[] bytes = new byte[3];
      WriteBuffer.wrap(bytes).writeUtf8(test);
      assertThat(bytes)
          .containsExactly('a', '?', 'c');
    }
  }

  @Test public void utf8_21Bit_truncated() {
    // https://en.wikipedia.org/wiki/Mahjong_Tiles_(Unicode_block)
    char[] array = "\uD83C\uDC00\uD83C\uDC01".toCharArray();
    array[array.length - 1] = 'c';
    String test = new String(array, 0, array.length - 1);
    assertThat(WriteBuffer.utf8SizeInBytes(test))
        .isEqualTo(5);

    byte[] bytes = new byte[5];
    WriteBuffer.wrap(bytes).writeUtf8(test);
    assertThat(new String(bytes, UTF_8))
        .isEqualTo("\uD83C\uDC00?");
  }

  @Test public void utf8_21Bit_brokenLowSurrogate() {
    // https://en.wikipedia.org/wiki/Mahjong_Tiles_(Unicode_block)
    char[] array = "\uD83C\uDC00\uD83C\uDC01".toCharArray();
    array[array.length - 1] = 'c';
    String test = new String(array);
    assertThat(WriteBuffer.utf8SizeInBytes(test))
        .isEqualTo(6);

    byte[] bytes = new byte[6];
    WriteBuffer.wrap(bytes).writeUtf8(test);
    assertThat(new String(bytes, UTF_8))
        .isEqualTo("\uD83C\uDC00?c");
  }

  @Test public void utf8_matchesJRE() {
    // examples from http://utf8everywhere.org/
    for (String string : Arrays.asList(
        "Приве́т नमस्ते שָׁלוֹם",
        "ю́ cyrillic small letter yu with acute",
        "∃y ∀x ¬(x ≺ y)"
    )) {
      int encodedSize = WriteBuffer.utf8SizeInBytes(string);
      assertThat(encodedSize)
          .isEqualTo(string.getBytes(UTF_8).length);

      byte[] bytes = new byte[encodedSize];
      WriteBuffer.wrap(bytes).writeUtf8(string);
      assertThat(new String(bytes, UTF_8))
          .isEqualTo(string);
    }
  }

  @Test public void utf8_matchesAscii() {
    String ascii = "86154a4ba6e913854d1e00c0db9010db";
    int encodedSize = WriteBuffer.utf8SizeInBytes(ascii);
    assertThat(encodedSize)
        .isEqualTo(ascii.length());

    byte[] bytes = new byte[encodedSize];
    WriteBuffer.wrap(bytes).writeAscii(ascii);
    assertThat(new String(bytes, UTF_8))
        .isEqualTo(ascii);

    WriteBuffer.wrap(bytes).writeUtf8(ascii);
    assertThat(new String(bytes, UTF_8))
        .isEqualTo(ascii);
  }

  @Test public void emoji() {
    byte[] emojiBytes = {(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x81};
    String emoji = new String(emojiBytes, UTF_8);
    assertThat(WriteBuffer.utf8SizeInBytes(emoji))
        .isEqualTo(emojiBytes.length);

    byte[] bytes = new byte[emojiBytes.length];
    WriteBuffer.wrap(bytes).writeUtf8(emoji);
    assertThat(bytes)
        .isEqualTo(emojiBytes);
  }

  @Test public void writeAscii_long() {
    assertThat(writeAscii(-1005656679588439279L))
        .isEqualTo("-1005656679588439279");
    assertThat(writeAscii(0L))
        .isEqualTo("0");
    assertThat(writeAscii(-9223372036854775808L /* Long.MIN_VALUE */))
        .isEqualTo("-9223372036854775808");
    assertThat(writeAscii(123456789L))
        .isEqualTo("123456789");
  }

  static String writeAscii(long v) {
    byte[] bytes = new byte[WriteBuffer.asciiSizeInBytes(v)];
    WriteBuffer.wrap(bytes).writeAscii(v);
    return new String(bytes, UTF_8);
  }

  // Test creating Buffer for a long string
  @Test public void writeString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 100000; i++) {
      builder.append("a");
    }
    String string = builder.toString();
    byte[] bytes = new byte[string.length()];
    WriteBuffer.wrap(bytes).writeAscii(string);
    assertThat(new String(bytes, UTF_8)).isEqualTo(string);
  }
}
