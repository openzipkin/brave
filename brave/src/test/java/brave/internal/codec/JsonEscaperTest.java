/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.internal.codec;

import org.junit.jupiter.api.Test;

import static brave.internal.codec.JsonEscaper.jsonEscape;
import static brave.internal.codec.JsonEscaper.jsonEscapedSizeInBytes;
import static org.assertj.core.api.Assertions.assertThat;

// initially a copy of zipkin2.internal.JsonEscaperTest
class JsonEscaperTest {

  @Test void testJsonEscapedSizeInBytes() {
    assertThat(jsonEscapedSizeInBytes(new String(new char[] {0, 'a', 1})))
        .isEqualTo(13);
    assertThat(jsonEscapedSizeInBytes(new String(new char[] {'"', '\\', '\t', '\b'})))
        .isEqualTo(8);
    assertThat(jsonEscapedSizeInBytes(new String(new char[] {'\n', '\r', '\f'})))
        .isEqualTo(6);
    assertThat(jsonEscapedSizeInBytes("\u2028 and \u2029"))
        .isEqualTo(17);
    assertThat(jsonEscapedSizeInBytes("\"foo"))
        .isEqualTo(5);
  }

  byte[] buf = new byte[20];

  @Test void testJsonEscape() {
    WriteBuffer buffer = new WriteBuffer(buf, 0);
    jsonEscape(new String(new char[] {0, 'a', 1}), buffer);
    assertThat(buffer).hasToString("\\u0000a\\u0001");

    buffer.pos = 0;
    jsonEscape(new String(new char[] {'"', '\\', '\t', '\b'}), buffer);
    assertThat(buffer).hasToString("\\\"\\\\\\t\\b");

    buffer.pos = 0;
    jsonEscape(new String(new char[] {'\n', '\r', '\f'}), buffer);
    assertThat(buffer).hasToString("\\n\\r\\f");

    buffer.pos = 0;
    jsonEscape("\u2028 and \u2029", buffer);
    assertThat(buffer).hasToString("\\u2028 and \\u2029");

    buffer.pos = 0;
    jsonEscape("\"foo", buffer);
    assertThat(buffer).hasToString("\\\"foo");
  }
}
