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

/**
 * Most of our parsing tools accept {@link CharSequence} instead of {@link String} to avoid
 * unnecessary allocation. This contains common functions available on {@link String}, preferring
 * signatures that match our utilities such as {@link EntrySplitter}.
 */
public final class CharSequences {

  /**
   * Joins two character sequences together. Inputs must be immutable. This is useful when the
   * result is only scanned. It does not implement {@link #toString()} or {@link #equals(Object)}.
   */
  public static CharSequence concat(CharSequence left, CharSequence right) {
    if (left == null) throw new NullPointerException("left == null");
    if (right == null) throw new NullPointerException("right == null");
    if (left.length() == 0) return right; // isEmpty is Java 15+
    if (right.length() == 0) return left;
    return new ConcatCharSequence(left, right);
  }

  static final class ConcatCharSequence implements CharSequence {
    final CharSequence left, right;
    final int length;

    ConcatCharSequence(CharSequence left, CharSequence right) {
      this.left = left;
      this.right = right;
      this.length = left.length() + right.length();
    }

    @Override public int length() {
      return length;
    }

    @Override public char charAt(int index) {
      if (index < 0) throw new IndexOutOfBoundsException("index < 0");
      if (index >= length) throw new IndexOutOfBoundsException("index >= length");
      int leftLength = left.length();
      if (index < leftLength) return left.charAt(index);
      return right.charAt(index - leftLength);
    }

    @Override public CharSequence subSequence(int beginIndex, int endIndex) {
      if (regionLength(length, beginIndex, endIndex) == 0) return "";
      if (beginIndex == 0 && endIndex == length) return this;

      int leftLength = left.length();
      // Exit early if the subSequence is only left or right
      if (beginIndex == 0 && endIndex == leftLength) return left;
      if (beginIndex == leftLength && endIndex == length) return right;

      // Exit early if the subSequence is contained by left or right
      if (endIndex <= leftLength) {
        return left.subSequence(beginIndex, endIndex);
      } else if (beginIndex > leftLength) {
        return right.subSequence(beginIndex - leftLength, endIndex - leftLength);
      }

      return new ConcatCharSequence( // we are in the middle
        left.subSequence(beginIndex, leftLength),
        right.subSequence(0, endIndex - leftLength)
      );
    }

    @Override public String toString() {
      return String.valueOf(left) + right;
    }
  }

  /**
   * Returns true if the input range contains only the expected characters.
   *
   * @param expected   characters to search for in the input
   * @param input      charSequence to search for {@code expected}
   * @param beginIndex begin index of the {@code input}, inclusive
   * @param endIndex   end index of the {@code input}, exclusive
   * @return true if we reached the {@code endIndex} without failures.
   * @see String#regionMatches(int, String, int, int) similar, except for {@link String}
   */
  public static boolean regionMatches(
    CharSequence expected, CharSequence input, int beginIndex, int endIndex) {
    if (expected == null) throw new NullPointerException("expected == null");
    if (input == null) throw new NullPointerException("input == null");
    int regionLength = regionLength(input.length(), beginIndex, endIndex);
    if (expected.length() > regionLength) return false;
    for (int i = 0, inputIndex = beginIndex; i < regionLength; i++, inputIndex++) {
      if (expected.charAt(i) != input.charAt(inputIndex)) return false;
    }
    return true;
  }

  /** Opposite of {@link CharSequence#subSequence(int, int)} */
  public static CharSequence withoutSubSequence(CharSequence input, int beginIndex, int endIndex) {
    if (input == null) throw new NullPointerException("input == null");
    int length = input.length();

    // Exit early if the region is empty or the entire input
    if (regionLength(length, beginIndex, endIndex) == 0) return input;
    if (beginIndex == 0 && endIndex == length) return "";

    // Exit early if the region ends on a boundary.
    if (beginIndex == 0) return input.subSequence(endIndex, length);
    if (endIndex == length) return input.subSequence(0, beginIndex);

    // Otherwise, the region to skip in the middle
    return new ConcatCharSequence(
      input.subSequence(0, beginIndex),
      input.subSequence(endIndex, length)
    );
  }

  static int regionLength(int inputLength, int beginIndex, int endIndex) {
    if (beginIndex < 0) throw new IndexOutOfBoundsException("beginIndex < 0");
    if (endIndex < 0) throw new IndexOutOfBoundsException("endIndex < 0");
    if (beginIndex > endIndex) throw new IndexOutOfBoundsException("beginIndex > endIndex");
    int regionLength = endIndex - beginIndex;
    if (endIndex > inputLength) throw new IndexOutOfBoundsException("endIndex > input");
    return regionLength;
  }
}
