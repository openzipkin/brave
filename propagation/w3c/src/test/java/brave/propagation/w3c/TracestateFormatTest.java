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
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TracestateFormatTest {

  // all these need log assertions
  @Test public void validateKey_empty() {
    assertThat(TracestateFormat.validateKey("")).isFalse();
  }

  @Test public void validateKey_unicode() {
    String key = "a_💩_day";
    assertThat(TracestateFormat.validateKey(key)).isFalse();
  }

  @Test public void validateKey_tooLong() {
    char[] tooMany = new char[257];
    assertThat(TracestateFormat.validateKey(new String(tooMany))).isFalse();
  }

  @Test public void validateKey_shortest() {
    assertThat(TracestateFormat.validateKey("a")).isTrue();
  }

  @Test public void validateKey_longest() {
    char[] longest = new char[256];
    Arrays.fill(longest, 'a');
    assertThat(TracestateFormat.validateKey(new String(longest))).isTrue();
  }
}
