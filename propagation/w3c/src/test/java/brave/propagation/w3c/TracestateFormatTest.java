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

import java.util.Arrays;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    assertThatThrownByValidateKey("")
        .hasMessage("Invalid input: empty");
  }

  @Test public void validateKey_tooLong() {
    char[] tooMany = new char[257];
    Arrays.fill(tooMany, 'a');
    assertThatThrownByValidateKey(new String(tooMany))
        .hasMessage("Invalid input: too large");
  }

  @Test public void validateKey_shortest_basic() {
    assertThatValidateKey("z").isTrue();
  }

  @Test public void validateKey_shortest_tenant() {
    assertThatValidateKey("0@z").isTrue();
    assertThatValidateKey("a@z").isTrue();
  }

  @Test public void validateKey_longest_basic() {
    assertThatValidateKey(LONGEST_BASIC_KEY).isTrue();
  }

  @Test public void validateKey_longest_tenant() {
    assertThatValidateKey(LONGEST_TENANT_KEY).isTrue();
  }

  @Test public void validateKey_invalid_basic() {
    // zero is allowed only as when there is an '@'
    assertThatThrownByValidateKey("0")
        .hasMessage("Invalid input: vendor must start with a-z");
  }

  @Test public void validateKey_invalid_basic_unicode() {
    Stream.of("a💩", "💩a").forEach(key -> assertThatThrownByValidateKey(key)
        .hasMessage("Invalid input: valid characters are: a-z 0-9 _ - * / @"));
  }

  @Test public void validateKey_invalid_tenant() {
    assertThatThrownByValidateKey("_@z")
        .hasMessage("Invalid input: tenant ID must start with a-z");
  }

  @Test public void validateKey_invalid_tenant_unicode() {
    Stream.of(
        "a@a💩",
        "a@💩a",
        "a💩@a",
        "💩a@a"
    ).forEach(key -> assertThatThrownByValidateKey(key)
        .hasMessage("Invalid input: valid characters are: a-z 0-9 _ - * / @"));
  }

  @Test public void validateKey_invalid_tenant_empty() {
    assertThatThrownByValidateKey("@a")
        .hasMessage("Invalid input: empty tenant ID");
    assertThatThrownByValidateKey("a@")
        .hasMessage("Invalid input: empty vendor");
  }

  @Test public void validateKey_invalid_tenant_vendor_longest() {
    assertThatValidateKey("a@abcdef12345678").isTrue();
  }

  @Test public void validateKey_invalid_tenant_vendor_tooLong() {
    assertThatThrownByValidateKey("a@abcdef1234567890")
        .hasMessage("Invalid input: vendor too long");
  }

  static AbstractBooleanAssert<?> assertThatValidateKey(String key) {
    return assertThat(TracestateFormat.validateKey(key, true));
  }

  static AbstractThrowableAssert<?, ? extends Throwable> assertThatThrownByValidateKey(String key) {
    return assertThatThrownBy(() -> TracestateFormat.validateKey(key, true))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
