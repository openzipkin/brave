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
import static org.assertj.core.api.Assertions.assertThat;

// initially a copy of zipkin2.internal.JsonEscaperTest
public class JsonEscaperTest {
  @Test public void testJsonEscape() {
    StringBuilder builder = new StringBuilder();
    jsonEscape(new String(new char[] {0, 'a', 1}), builder);
    assertThat(builder).hasToString("\\u0000a\\u0001");

    builder.setLength(0);
    jsonEscape(new String(new char[] {'"', '\\', '\t', '\b'}), builder);
    assertThat(builder).hasToString("\\\"\\\\\\t\\b");

    builder.setLength(0);
    jsonEscape(new String(new char[] {'\n', '\r', '\f'}), builder);
    assertThat(builder).hasToString("\\n\\r\\f");

    builder.setLength(0);
    jsonEscape("\u2028 and \u2029", builder);
    assertThat(builder).hasToString("\\u2028 and \\u2029");

    builder.setLength(0);
    jsonEscape("\"foo", builder);
    assertThat(builder).hasToString("\\\"foo");
  }
}
