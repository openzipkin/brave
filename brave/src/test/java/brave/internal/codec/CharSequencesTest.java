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

import java.nio.CharBuffer;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CharSequencesTest {
  @Test public void regionMatches() {
    assertThat(CharSequences.regionMatches("b3", "b3=1", 0, 2)).isTrue();
    assertThat(CharSequences.regionMatches("b3", "b3=1", 1, 3)).isFalse();
    assertThat(CharSequences.regionMatches("1", "b3=1", 3, 4)).isTrue();

    assertThat(CharSequences.regionMatches(CharBuffer.wrap("b3"), "b3=1", 0, 2)).isTrue();
    assertThat(CharSequences.regionMatches(CharBuffer.wrap("b3"), "b3=1", 1, 3)).isFalse();
    assertThat(CharSequences.regionMatches(CharBuffer.wrap("1"), "b3=1", 3, 4)).isTrue();

    assertThat(CharSequences.regionMatches("b3", CharBuffer.wrap("b3=1"), 0, 2)).isTrue();
    assertThat(CharSequences.regionMatches("b3", CharBuffer.wrap("b3=1"), 1, 3)).isFalse();
    assertThat(CharSequences.regionMatches("1", CharBuffer.wrap("b3=1"), 3, 4)).isTrue();
  }

  @Test public void regionMatches_badParameters() {
    assertThatThrownBy(() -> CharSequences.regionMatches(null, "b3", 0, 0))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("expected == null");
    assertThatThrownBy(() -> CharSequences.regionMatches("b3", null, 0, 0))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("input == null");

    assertThatThrownBy(() -> CharSequences.regionMatches("b3", "a", -1, 1))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("beginIndex < 0");

    assertThatThrownBy(() -> CharSequences.regionMatches("b3", "a", 0, -1))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("endIndex < 0");

    assertThatThrownBy(() -> CharSequences.regionMatches("b3", "a", 1, 0))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("beginIndex > endIndex");

    assertThatThrownBy(() -> CharSequences.regionMatches("b3", "a", 0, 2))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("endIndex > input");
  }

  @Test public void concat() {
    assertThat(CharSequences.concat("a", "b")).hasToString("ab");
    assertThat(CharSequences.concat("a", ",b")).hasToString("a,b");

    assertThat(CharSequences.concat(CharBuffer.wrap("a"), "b")).hasToString("ab");
    assertThat(CharSequences.concat("a", CharBuffer.wrap("b"))).hasToString("ab");
  }

  @Test public void concat_charAt() {
    String left = "b3=1", right = "azure=b";
    CharSequence concated = new CharSequences.ConcatCharSequence(left, right);
    String normalConcated = left + right;
    for (int i = 0; i < normalConcated.length(); i++) {
      assertThat(concated.charAt(i)).isEqualTo(normalConcated.charAt(i));
    }
  }

  @Test public void concat_empties() {
    assertThat(CharSequences.concat("", "")).isEmpty();
    assertThat(CharSequences.concat("a", "")).isEqualTo("a");
    assertThat(CharSequences.concat("", "b")).isEqualTo("b");
  }

  @Test public void concat_badParameters() {
    assertThatThrownBy(() -> CharSequences.concat(null, ""))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("left == null");
    assertThatThrownBy(() -> CharSequences.concat("", null))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("right == null");

    CharSequence concated = CharSequences.concat("1", "2");
    assertThatThrownBy(() -> concated.subSequence(-1, 1))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("beginIndex < 0");

    assertThatThrownBy(() -> concated.subSequence(0, -1))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("endIndex < 0");

    assertThatThrownBy(() -> concated.subSequence(1, 0))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("beginIndex > endIndex");

    assertThatThrownBy(() -> concated.subSequence(0, 5))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("endIndex > input");
  }

  @Test public void concat_subSequence() {
    String left = "b3=1,", right = "es=2";
    CharSequence concat = new CharSequences.ConcatCharSequence(left, right);
    assertThat(concat.subSequence(0, 0)).isEmpty();
    assertThat(concat.subSequence(0, concat.length())).isSameAs(concat);

    assertThat(concat.subSequence(0, left.length())).isSameAs(left);
    assertThat(concat.subSequence(left.length(), concat.length())).isSameAs(right);

    assertThat(concat.subSequence(0, 2)).hasToString("b3");
    assertThat(concat.subSequence(2, 4)).hasToString("=1");
    assertThat(concat.subSequence(4, 6)).hasToString(",e");
    assertThat(concat.subSequence(6, 8)).hasToString("s=");

    assertThat(concat.subSequence(1, 9)).hasToString("3=1,es=2");
    assertThat(concat.subSequence(0, 8)).hasToString("b3=1,es=");
  }

  @Test public void withoutSubSequence() {
    String input = "b3=1,es=2";
    assertThat(CharSequences.withoutSubSequence(input, 0, 0)).isSameAs(input);
    assertThat(CharSequences.withoutSubSequence(input, 0, input.length())).isEmpty();

    assertThat(CharSequences.withoutSubSequence(input, 0, 2)).hasToString("=1,es=2");
    assertThat(CharSequences.withoutSubSequence(input, 2, 4)).hasToString("b3,es=2");
    assertThat(CharSequences.withoutSubSequence(input, 4, 6)).hasToString("b3=1s=2");
    assertThat(CharSequences.withoutSubSequence(input, 6, 8)).hasToString("b3=1,e2");

    assertThat(CharSequences.withoutSubSequence(input, 1, 9)).hasToString("b");
    assertThat(CharSequences.withoutSubSequence(input, 0, 8)).hasToString("2");
  }

  @Test public void withoutSubSequence_badParameters() {
    assertThatThrownBy(() -> CharSequences.withoutSubSequence(null, 0, 0))
      .isInstanceOf(NullPointerException.class)
      .hasMessage("input == null");

    assertThatThrownBy(() -> CharSequences.withoutSubSequence("b3", -1, 1))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("beginIndex < 0");

    assertThatThrownBy(() -> CharSequences.withoutSubSequence("b3", 0, -1))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("endIndex < 0");

    assertThatThrownBy(() -> CharSequences.withoutSubSequence("b3", 1, 0))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("beginIndex > endIndex");

    assertThatThrownBy(() -> CharSequences.withoutSubSequence("b3", 0, 3))
      .isInstanceOf(IndexOutOfBoundsException.class)
      .hasMessage("endIndex > input");
  }
}
