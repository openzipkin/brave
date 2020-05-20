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

import static brave.internal.codec.JsonEscaper.jsonEscape;
import static brave.internal.codec.JsonEscaper.jsonEscapedSizeInBytes;
import static org.assertj.core.api.Assertions.assertThat;

// initially a copy of zipkin2.internal.JsonEscaperTest
public class JsonEscaperTest {

  @Test public void testJsonEscapedSizeInBytes() {
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

  @Test public void testJsonEscape() {
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
