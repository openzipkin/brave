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
package brave.propagation.w3c;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TracestateFormatTest {
  static final String FORTY_KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_-*/";
  static final String TWO_HUNDRED_FORTY_KEY_CHARS =
    FORTY_KEY_CHARS + FORTY_KEY_CHARS + FORTY_KEY_CHARS
      + FORTY_KEY_CHARS + FORTY_KEY_CHARS + FORTY_KEY_CHARS;

  static final String LONGEST_BASIC_KEY =
    TWO_HUNDRED_FORTY_KEY_CHARS + FORTY_KEY_CHARS.substring(0, 16);

  static final String LONGEST_TENANT_KEY =
    "1" + TWO_HUNDRED_FORTY_KEY_CHARS + "@" + FORTY_KEY_CHARS.substring(0, 13);

  // all these need log assertions
  @Test public void validateKey_empty() {
    assertThat(TracestateFormat.validateKey("")).isFalse();
  }

  @Test public void validateKey_tooLong() {
    char[] tooMany = new char[257];
    assertThat(TracestateFormat.validateKey(new String(tooMany))).isFalse();
  }

  @Test public void validateKey_shortest_basic() {
    assertThat(TracestateFormat.validateKey("z")).isTrue();
  }

  @Test public void validateKey_shortest_tenant() {
    assertThat(TracestateFormat.validateKey("0@z")).isTrue();
    assertThat(TracestateFormat.validateKey("a@z")).isTrue();
  }

  @Test public void validateKey_longest_basic() {
    assertThat(TracestateFormat.validateKey(LONGEST_BASIC_KEY)).isTrue();
  }

  @Test public void validateKey_longest_tenant() {
    assertThat(TracestateFormat.validateKey(LONGEST_TENANT_KEY)).isTrue();
  }

  @Test public void validateKey_invalid_basic() {
    // zero is allowed only as when there is an '@'
    assertThat(TracestateFormat.validateKey("0")).isFalse();
  }

  @Test public void validateKey_invalid_basic_unicode() {
    assertThat(TracestateFormat.validateKey("a💩")).isFalse();
    assertThat(TracestateFormat.validateKey("💩a")).isFalse();
  }

  @Test public void validateKey_invalid_tenant() {
    assertThat(TracestateFormat.validateKey("_@z")).isFalse();
  }

  @Test public void validateKey_invalid_tenant_unicode() {
    assertThat(TracestateFormat.validateKey("a@a💩")).isFalse();
    assertThat(TracestateFormat.validateKey("a@💩a")).isFalse();
    assertThat(TracestateFormat.validateKey("a💩@a")).isFalse();
    assertThat(TracestateFormat.validateKey("💩a@a")).isFalse();
  }

  @Test public void validateKey_invalid_tenant_empty() {
    assertThat(TracestateFormat.validateKey("@a")).isFalse();
    assertThat(TracestateFormat.validateKey("a@")).isFalse();
  }

  @Test public void validateKey_invalid_tenant_vendor_longest() {
    assertThat(TracestateFormat.validateKey("a@abcdef12345678")).isTrue();
  }

  @Test public void validateKey_invalid_tenant_vendor_tooLong() {
    assertThat(TracestateFormat.validateKey("a@abcdef1234567890")).isFalse();
  }
}
