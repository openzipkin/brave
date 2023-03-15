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
    int skippedRegionLength = regionLength(length, beginIndex, endIndex);
    if (skippedRegionLength == 0) return input;
    if (beginIndex == 0 && endIndex == length) return "";

    // Exit early if the region ends on a boundary.
    // This doesn't use input.subsequence as it might allocate a String
    if (beginIndex == 0) return new SubSequence(input, endIndex, length);
    if (endIndex == length) return new SubSequence(input, 0, beginIndex);

    // Otherwise, the region to skip in the middle
    return new WithoutSubSequence(input, 0, beginIndex, endIndex, length);
  }

  static int regionLength(int inputLength, int beginIndex, int endIndex) {
    if (beginIndex < 0) throw new IndexOutOfBoundsException("beginIndex < 0");
    if (endIndex < 0) throw new IndexOutOfBoundsException("endIndex < 0");
    if (beginIndex > endIndex) throw new IndexOutOfBoundsException("beginIndex > endIndex");
    int regionLength = endIndex - beginIndex;
    if (endIndex > inputLength) throw new IndexOutOfBoundsException("endIndex > input");
    return regionLength;
  }

  /** Avoids implicit string allocation when the input calls {@link String#subSequence(int, int)} */
  static final class SubSequence implements CharSequence {
    final CharSequence input;
    final int begin, end, length;

    SubSequence(CharSequence input, int begin, int end) {
      this.input = input;
      this.begin = begin;
      this.end = end;
      this.length = end - begin;
    }

    @Override public int length() {
      return length;
    }

    @Override public char charAt(int index) {
      if (index < 0) throw new IndexOutOfBoundsException("index < 0");
      if (index >= length) throw new IndexOutOfBoundsException("index >= length");
      return input.charAt(begin + index);
    }

    @Override public CharSequence subSequence(int beginIndex, int endIndex) {
      int newLength = regionLength(length, beginIndex, endIndex);
      if (newLength == 0) return "";
      if (newLength == length) return this;
      return new SubSequence(input, beginIndex + begin, endIndex + begin);
    }

    @Override public String toString() {
      return new StringBuilder(length).append(input, begin, end).toString();
    }
  }

  static final class WithoutSubSequence implements CharSequence {
    final CharSequence input;
    final int begin, beginSkip, endSkip, end, skipLength, length;

    WithoutSubSequence(
      CharSequence input, int begin, int beginSkip, int endSkip, int end) {
      this.input = input;
      this.begin = begin;
      this.beginSkip = beginSkip;
      this.endSkip = endSkip;
      this.end = end;
      this.skipLength = endSkip - beginSkip;
      this.length = end - begin - skipLength;
    }

    @Override public int length() {
      return length;
    }

    @Override public char charAt(int index) {
      if (index < 0) throw new IndexOutOfBoundsException("index < 0");
      if (index >= length) throw new IndexOutOfBoundsException("index >= length");
      index += begin;
      if (index >= beginSkip) index += skipLength;
      return input.charAt(index);
    }

    @Override public CharSequence subSequence(int beginIndex, int endIndex) {
      int newLength = regionLength(length, beginIndex, endIndex);
      if (newLength == 0) return "";
      if (newLength == length) return this;

      // Move the input positions to the relative offset
      beginIndex += begin;
      endIndex += begin;

      // Check to see if we are before the skipped region
      if (endIndex <= beginSkip) return new SubSequence(input, beginIndex, endIndex);

      // We now know we either include the skipped region or start after it
      endIndex += skipLength;

      // If we are after the skipped region, return a subsequence
      if (beginIndex >= beginSkip) return new SubSequence(input, beginIndex + skipLength, endIndex);

      // We happened to require both sides of the skipped region, so narrow it according to inputs.
      return new WithoutSubSequence(input, beginIndex, beginSkip, endSkip, endIndex);
    }

    @Override public String toString() {
      // Careful here to use .append(input, begin, end), not .append(input.subsequence(begin, end))
      // The latter can allocate temporary strings, subverting the purpose of using StringBuilder!
      return new StringBuilder(length)
        .append(input, begin, beginSkip)
        .append(input, endSkip, end).toString();
    }
  }
}
