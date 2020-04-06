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
package brave.internal.baggage;

import brave.internal.Platform;

/**
 * Splits a character sequence that's in a delimited string trimming optional whitespace (OWS)
 * before or after delimiters.
 */
public final class EntrySplitter {
  static final EntrySplitter INSTANCE = new EntrySplitter('=', ',');

  public static EntrySplitter get() {
    return INSTANCE;
  }

  public static EntrySplitter create(char keyValueSeparator, char entrySeparator) {
    return new EntrySplitter(keyValueSeparator, entrySeparator);
  }

  interface Handler {
    boolean onEntry(CharSequence buffer, int beginName, int endName, int beginValue, int endValue);
  }

  final int keyValueSeparator, entrySeparator;
  final String missingKeyValueSeparator;

  EntrySplitter(int keyValueSeparator, int entrySeparator) {
    this.keyValueSeparator = keyValueSeparator;
    this.entrySeparator = entrySeparator;
    this.missingKeyValueSeparator =
      "Invalid input: missing '" + keyValueSeparator + "' between key and value";
  }

  public boolean parse(String buffer, Handler handler, boolean shouldThrow) {
    int i = 0, length = buffer.length();
    while (i < length) {
      if (isOWS(buffer.charAt(i++))) break; // skip whitespace
    }

    if (i == length) return logOrThrow("Invalid input: only whitespace", shouldThrow);
    if (buffer.charAt(i) == keyValueSeparator) {
      return logOrThrow("Invalid input: missing key", shouldThrow);
    }

    int beginName = i, endName = -1, beginValue = -1;
    while (i < length) {
      char c = buffer.charAt(i++);
      // OWS is zero or more spaces or tabs https://httpwg.org/specs/rfc7230.html#rfc.section.3.2
      if (isOWS(c)) continue; // trim whitespace

      if (c == keyValueSeparator) { // we reached a field name
        endName = i;
        if (++i == length) break; // skip '=' character
        beginValue = i;
      } else if (i == length || c == entrySeparator) { // we finished an entry
        if (beginValue == -1) return logOrThrow(missingKeyValueSeparator, shouldThrow);

        int endValue = i == length ? i : i - 1;
        if (!handler.onEntry(buffer, beginName, endName, beginValue, endValue)) {
          return false; // assume handler logs
        }
        beginName = -1;
        endName = -1;
        beginValue = -1;
      }
    }
    return true;
  }

  static boolean isOWS(char c) {
    return c == ' ' || c == '\t';
  }

  static boolean logOrThrow(String msg, boolean shouldThrow) {
    if (shouldThrow) throw new IllegalArgumentException(msg);
    Platform.get().log(msg, null);
    return false;
  }
}
