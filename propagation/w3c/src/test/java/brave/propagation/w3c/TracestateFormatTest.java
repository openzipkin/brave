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

  TracestateFormat tracestateFormat = new TracestateFormat(true);

  // all these need log assertions
  @Test public void validateKey_empty() {
    assertThatThrownByValidateKey("")
        .hasMessage("Invalid key: empty");
  }

  @Test public void validateKey_tooLong() {
    char[] tooMany = new char[257];
    Arrays.fill(tooMany, 'a');
    assertThatThrownByValidateKey(new String(tooMany))
        .hasMessage("Invalid key: too large");
  }

  @Test public void validateKey_specialCharacters() {
    for (char allowedSpecial : Arrays.asList('@', '_', '-', '*', '/')) {
      assertThatThrownByValidateKey(allowedSpecial + "")
          .hasMessage("Invalid key: must start with a-z 0-9");
      assertThatValidateKey("a" + allowedSpecial).isTrue();
      // Any number of special characters are allowed. ex "a*******", "a@@@@@@@"
      // https://github.com/w3c/trace-context/pull/386
      assertThatValidateKey("a" + allowedSpecial + allowedSpecial).isTrue();
      assertThatValidateKey("a" + allowedSpecial + "1").isTrue();
    }
  }

  @Test public void validateKey_longest_basic() {
    assertThatValidateKey(LONGEST_BASIC_KEY).isTrue();
  }

  @Test public void validateKey_longest_tenant() {
    assertThatValidateKey(LONGEST_TENANT_KEY).isTrue();
  }

  @Test public void validateKey_shortest() {
    for (char n = '0'; n <= '9'; n++) {
      assertThatValidateKey(String.valueOf(n)).isTrue();
    }
    for (char l = 'a'; l <= 'z'; l++) {
      assertThatValidateKey(String.valueOf(l)).isTrue();
    }
  }

  @Test public void validateKey_invalid_unicode() {
    assertThatThrownByValidateKey("a💩")
        .hasMessage("Invalid key: valid characters are: a-z 0-9 _ - * / @");
    assertThatThrownByValidateKey("💩a")
        .hasMessage("Invalid key: must start with a-z 0-9");
  }

  AbstractBooleanAssert<?> assertThatValidateKey(String key) {
    return assertThat(tracestateFormat.validateKey(key, 0, key.length()));
  }

  AbstractThrowableAssert<?, ? extends Throwable> assertThatThrownByValidateKey(String key) {
    return assertThatThrownBy(() -> tracestateFormat.validateKey(key, 0, key.length()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
