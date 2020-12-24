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

import brave.internal.codec.CharSequences.SubSequence;
import brave.internal.codec.CharSequences.WithoutSubSequence;
import java.nio.CharBuffer;
import org.junit.Test;

import static java.util.Arrays.asList;
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

  @Test public void withoutSubSequence() {
    String input = "b3=1,es=2";
    assertThat(CharSequences.withoutSubSequence(input, 0, 0))
      .isSameAs(input);
    assertThat(CharSequences.withoutSubSequence(input, 0, input.length()))
      .isInstanceOf(String.class).isEmpty();

    assertThat(CharSequences.withoutSubSequence(input, 0, 2))
      .isInstanceOf(SubSequence.class).hasToString("=1,es=2");
    assertThat(CharSequences.withoutSubSequence(input, 2, 4))
      .isInstanceOf(WithoutSubSequence.class).hasToString("b3,es=2");
    assertThat(CharSequences.withoutSubSequence(input, 4, 6))
      .isInstanceOf(WithoutSubSequence.class).hasToString("b3=1s=2");
    assertThat(CharSequences.withoutSubSequence(input, 6, 8))
      .isInstanceOf(WithoutSubSequence.class).hasToString("b3=1,e2");

    assertThat(CharSequences.withoutSubSequence(input, 1, 9))
      .isInstanceOf(SubSequence.class).hasToString("b");
    assertThat(CharSequences.withoutSubSequence(input, 0, 8))
      .isInstanceOf(SubSequence.class).hasToString("2");
  }

  @Test public void withoutSubSequence_charAt() {
    String input = "b3=1,es=2";

    for (CharSequence sequence : asList(
      CharSequences.withoutSubSequence(input, 0, 2),
      CharSequences.withoutSubSequence(input, 2, 4),
      CharSequences.withoutSubSequence(input, 4, 6),
      CharSequences.withoutSubSequence(input, 6, 8),
      CharSequences.withoutSubSequence(input, 1, 9),
      CharSequences.withoutSubSequence(input, 0, 8))) {
      String string = sequence.toString(); // we know this is ok as it is tested above
      for (int i = 0; i < string.length(); i++) {
        assertThat(sequence.charAt(i))
          .isEqualTo(string.charAt(i));
      }
    }
  }

  @Test public void withoutSubSequence_length() {
    String input = "b3=1,es=2";

    for (CharSequence sequence : asList(
      CharSequences.withoutSubSequence(input, 0, 2),
      CharSequences.withoutSubSequence(input, 2, 4),
      CharSequences.withoutSubSequence(input, 4, 6),
      CharSequences.withoutSubSequence(input, 6, 8),
      CharSequences.withoutSubSequence(input, 1, 9),
      CharSequences.withoutSubSequence(input, 0, 8))) {
      String string = sequence.toString(); // we know this is ok as it is tested above
      assertThat(sequence.length()).isEqualTo(string.length());
    }
  }

  @Test public void withoutSubSequence_subsequence() {
    String realInput = "~b3=1!@#$%^&*,es=2"; // fill skipped area with junk so failures are obvious

    CharSequence input = new WithoutSubSequence(realInput, 1, 5, 13, realInput.length());
    assertThat(input).hasToString("b3=1,es=2");

    assertThat(input.subSequence(0, input.length())).isSameAs(input);
    assertThat(input.subSequence(0, 0)).isInstanceOf(String.class).isEmpty();

    assertThat(input.subSequence(0, 2)).isInstanceOf(SubSequence.class).hasToString("b3");
    assertThat(input.subSequence(2, 4)).isInstanceOf(SubSequence.class).hasToString("=1");
    assertThat(input.subSequence(3, 5)).isInstanceOf(WithoutSubSequence.class).hasToString("1,");
    assertThat(input.subSequence(4, 6)).isInstanceOf(SubSequence.class).hasToString(",e");
    assertThat(input.subSequence(6, 8)).isInstanceOf(SubSequence.class).hasToString("s=");
    assertThat(input.subSequence(8, 9)).isInstanceOf(SubSequence.class).hasToString("2");
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
