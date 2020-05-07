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

import brave.internal.Platform;

/**
 * Splits a character sequence that's in a delimited string trimming optional whitespace (OWS)
 * before or after delimiters.
 *
 * <p>This is intended to be initialized as a constant, as doing so per-request will add
 * unnecessary overhead.
 */
public final class EntrySplitter {
  static final char END_OF_STRING = 0;

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    int maxEntries = Integer.MAX_VALUE;
    char entrySeparator = ',', keyValueSeparator = '=';
    boolean trimOWSAroundEntrySeparator = true, trimOWSAroundKeyValueSeparator = true;

    public Builder maxEntries(int maxEntries) {
      if (maxEntries == 0) throw new NullPointerException("maxEntries == 0");
      this.maxEntries = maxEntries;
      return this;
    }

    public Builder entrySeparator(char entrySeparator) {
      if (entrySeparator == 0) throw new NullPointerException("entrySeparator == 0");
      this.entrySeparator = entrySeparator;
      return this;
    }

    public Builder keyValueSeparator(char keyValueSeparator) {
      if (keyValueSeparator == 0) throw new NullPointerException("keyValueSeparator == 0");
      this.keyValueSeparator = keyValueSeparator;
      return this;
    }

    public Builder trimOWSAroundEntrySeparator(boolean trimOWSAroundEntrySeparator) {
      this.trimOWSAroundEntrySeparator = trimOWSAroundEntrySeparator;
      return this;
    }

    public Builder trimOWSAroundKeyValueSeparator(boolean trimOWSAroundKeyValueSeparator) {
      this.trimOWSAroundKeyValueSeparator = trimOWSAroundKeyValueSeparator;
      return this;
    }

    public EntrySplitter build() {
      return new EntrySplitter(this);
    }
  }

  /**
   * This is a callback on offsets to avoid allocating strings for a malformed input {@code
   * buffer}.
   *
   * @param <T> target of parsed entries
   */
  public interface Handler<T> {
    /**
     * Called for each valid entry split from the input {@code buffer}. Return {@code false} after
     * logging to stop due to invalid input.
     *
     * <p>After validating, typically strings will be parsed from the input like so:
     * <pre>{@code
     * String key = buffer.substring(beginKey, endKey);
     * String value = buffer.substring(beginValue, endValue);
     * }</pre>
     */
    boolean onEntry(
        T target, String buffer, int beginKey, int endKey, int beginValue, int endValue);
  }

  final char keyValueSeparator, entrySeparator;
  int maxEntries;
  final boolean trimOWSAroundEntrySeparator, trimOWSAroundKeyValueSeparator;
  final String missingKey, missingKeyValueSeparator, overMaxEntries;

  EntrySplitter(Builder builder) {
    keyValueSeparator = builder.keyValueSeparator;
    entrySeparator = builder.entrySeparator;
    maxEntries = builder.maxEntries;
    trimOWSAroundEntrySeparator = builder.trimOWSAroundEntrySeparator;
    trimOWSAroundKeyValueSeparator = builder.trimOWSAroundKeyValueSeparator;
    missingKey = "Invalid input: missing key before '" + entrySeparator + "'";
    missingKeyValueSeparator =
        "Invalid input: missing key value separator '" + keyValueSeparator + "'";
    overMaxEntries = "Invalid input: over " + maxEntries + " entries";
  }

  public <T> boolean parse(String buffer, Handler<T> handler, T target, boolean shouldThrow) {
    int remainingEntries = maxEntries, beginKey = -1, endKey = -1, beginValue = -1;
    for (int i = 0, length = buffer.length(); i < length; i++) {
      char c = buffer.charAt(i), next = i + 1 == length ? END_OF_STRING : buffer.charAt(i + 1);

      if (next == 0 || next == entrySeparator) { // finished an entry
        if (c == keyValueSeparator) beginValue = i + 1; // empty value: ex "key="

        if (beginKey == -1 && beginValue == -1) {
          continue; // ignore empty entries, like ",,"
        } else if (beginKey == -1 || endKey == beginKey) {
          return logOrThrow(missingKey, shouldThrow); // throw on "=" ",="
        } else if (endKey == -1) {
          return logOrThrow(missingKeyValueSeparator, shouldThrow); // throw on "k1" "k1=v2,k2"
        }

        if (remainingEntries-- == 0) logOrThrow(overMaxEntries, shouldThrow);

        int endValue = trimOWSAroundEntrySeparator ? rewindOWS(buffer, i) : i;

        if (!handler.onEntry(target, buffer, beginKey, endKey + 1, beginValue, endValue + 1)) {
          return false; // assume handler logs
        }
        beginKey = endKey = beginValue = -1;
      } else if (next == keyValueSeparator) { // reached a key
        endKey = trimOWSAroundKeyValueSeparator ? rewindOWS(buffer, i) : i;
      } else if (beginKey == -1) {
        if (trimOWSAroundEntrySeparator && isOWS(c)) continue;
        if (c == entrySeparator) continue; // skip the entrySeparator (ex ',')
        if (c == keyValueSeparator) return logOrThrow(missingKey, shouldThrow); // ex "=v1" ",=v2"
        beginKey = i;
      } else if (endKey != -1 && beginValue == -1) {
        if (trimOWSAroundKeyValueSeparator && isOWS(c)) continue;
        if (c == keyValueSeparator) continue; // skip the keyValueSeparator (ex '=')
        beginValue = i;
      }
    }
    return true;
  }

  static int rewindOWS(String buffer, int i) {
    while (isOWS(buffer.charAt(i))) {
      if (i-- == 0) return 0;// trim whitespace
    }
    return i;
  }

  // OWS is zero or more spaces or tabs https://httpwg.org/specs/rfc7230.html#rfc.section.3.2
  static boolean isOWS(char c) {
    return c == ' ' || c == '\t';
  }

  static boolean logOrThrow(String msg, boolean shouldThrow) {
    if (shouldThrow) throw new IllegalArgumentException(msg);
    Platform.get().log(msg, null);
    return false;
  }
}
