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

import brave.internal.Platform;
import brave.internal.codec.EntrySplitter;

/**
 * Implements https://w3c.github.io/trace-context/#tracestate-header
 *
 * <p>In the above specification, a tracestate entry is sometimes called member. The key of the
 * entry is most often called vendor name, but it is more about a tracing system vs something vendor
 * specific. We choose to not use the term vendor as this is open source code. Instead, we use term
 * entry (key/value).
 */
final class TracestateFormat implements EntrySplitter.Handler<Tracestate> {
  static final TracestateFormat INSTANCE = new TracestateFormat(false);

  final boolean shouldThrow;
  final EntrySplitter entrySplitter;

  TracestateFormat(boolean shouldThrow) {
    this.shouldThrow = shouldThrow;
    entrySplitter = EntrySplitter.newBuilder()
        .maxEntries(32) // https://w3c.github.io/trace-context/#tracestate-header-field-values
        .entrySeparator(',')
        .trimOWSAroundEntrySeparator(true) // https://w3c.github.io/trace-context/#list
        .keyValueSeparator('=')
        .trimOWSAroundKeyValueSeparator(false) // https://github.com/w3c/trace-context/issues/409
        .shouldThrow(shouldThrow)
        .build();
  }

  // Simplify parsing rules by allowing value-based lookup on an ASCII value.
  //
  // This approach is similar to io.netty.util.internal.StringUtil.HEX2B as it uses an array to
  // cache values. Unlike HEX2B, this requires a bounds check when using the character's integer
  // value as a key.
  //
  // The performance cost of a bounds check is still better than using BitSet, and avoids allocating
  // an array of 64 thousand booleans: that could be problematic in old JREs or Android.
  static int LAST_VALID_KEY_CHAR = 'z';
  static boolean[] VALID_KEY_CHARS = new boolean[LAST_VALID_KEY_CHAR + 1];

  static {
    for (char c = 0; c < VALID_KEY_CHARS.length; c++) {
      VALID_KEY_CHARS[c] = isValidTracestateKeyChar(c);
    }
  }

  static boolean isValidTracestateKeyChar(char c) {
    return isLetterOrNumber(c) || c == '@' || c == '_' || c == '-' || c == '*' || c == '/';
  }

  static boolean isLetterOrNumber(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
  }

  @Override
  public boolean onEntry(
      Tracestate target, String buffer, int beginKey, int endKey, int beginValue, int endValue) {
    if (!validateKey(buffer, beginKey, endKey)) return false;
    if (!validateValue(buffer, beginValue, beginValue)) return false;
    return target.put(buffer.substring(beginKey, endKey), buffer.substring(beginValue, endValue));
  }

  boolean parseInto(String tracestateString, Tracestate tracestate) {
    return entrySplitter.parse(this, tracestate, tracestateString);
  }

  /**
   * Performs validation according to the ABNF of the {@code tracestate} key.
   *
   * <p>See https://www.w3.org/TR/trace-context-1/#key
   */
  // Logic to narrow error messages is intentionally deferred.
  // Performance matters as this could be called up to 32 times per header.
  boolean validateKey(CharSequence buffer, int beginKey, int endKey) {
    int length = endKey - beginKey;
    if (length == 0) return logOrThrow("Invalid key: empty", shouldThrow);
    if (length > 256) return logOrThrow("Invalid key: too large", shouldThrow);
    char first = buffer.charAt(beginKey);
    if (!isLetterOrNumber(first)) {
      return logOrThrow("Invalid key: must start with a-z 0-9", shouldThrow);
    }

    for (int i = beginKey + 1; i < endKey; i++) {
      char c = buffer.charAt(i);

      if (c > LAST_VALID_KEY_CHAR || !VALID_KEY_CHARS[c]) {
        return logOrThrow("Invalid key: valid characters are: a-z 0-9 _ - * / @", shouldThrow);
      }
    }
    return true;
  }

  boolean validateValue(CharSequence buffer, int beginValue, int endValue) {
    // TODO: empty and whitespace-only allowed Ex. 'foo=' or 'foo=  '
    // There are likely other rules, so figure out what they are and implement.
    return true;
  }

  static boolean logOrThrow(String msg, boolean shouldThrow) {
    if (shouldThrow) throw new IllegalArgumentException(msg);
    Platform.get().log(msg, null);
    return false;
  }
}
