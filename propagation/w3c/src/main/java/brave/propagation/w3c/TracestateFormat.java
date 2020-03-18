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

import brave.internal.Nullable;

import static brave.propagation.w3c.TraceparentFormat.FORMAT_LENGTH;

/**
 * Implements https://w3c.github.io/trace-context/#tracestate-header
 *
 * <p>In the above specification, a tracestate entry is sometimes called member. The key of the
 * entry is most often called vendor name, but it is more about a tracing system vs something vendor
 * specific. We choose to not use the term vendor as this is open source code. Instead, we use term
 * entry (key/value).
 */
final class TracestateFormat {
  final String key;
  final int keyLength;
  final int entryLength;

  TracestateFormat(String key) {
    this.key = key;
    this.keyLength = key.length();
    this.entryLength = keyLength + 1 /* = */ + FORMAT_LENGTH;
  }

  enum Op {
    THIS_ENTRY,
    OTHER_ENTRIES
  }

  interface Handler {
    boolean onThisEntry(CharSequence tracestate, int beginIndex, int endIndex);
  }

  // TODO: SHOULD on 512 char limit https://w3c.github.io/trace-context/#tracestate-limits
  String write(String thisValue, CharSequence otherEntries) {
    int extraLength = otherEntries == null ? 0 : otherEntries.length();

    char[] result;
    if (extraLength == 0) {
      result = new char[entryLength];
    } else {
      result = new char[entryLength + 1 /* , */ + extraLength];
    }

    int pos = 0;
    for (int i = 0; i < keyLength; i++) {
      result[pos++] = key.charAt(i);
    }
    result[pos++] = '=';

    for (int i = 0, len = thisValue.length(); i < len; i++) {
      result[pos++] = thisValue.charAt(i);
    }

    if (extraLength > 0) { // Append others after ours
      result[pos++] = ',';
      for (int i = 0; i < extraLength; i++) {
        result[pos++] = otherEntries.charAt(i);
      }
    }
    return new String(result, 0, pos);
  }

  // TODO: characters were added to the valid list, so it is possible this impl no longer works
  // TODO: 32 max entries https://w3c.github.io/trace-context/#tracestate-header-field-values
  // TODO: empty and whitespace-only allowed Ex. 'foo=' or 'foo=  '
  @Nullable CharSequence parseAndReturnOtherEntries(String tracestate, Handler handler) {
    StringBuilder currentString = new StringBuilder(), otherEntries = null;
    Op op;
    OUTER:
    for (int i = 0, length = tracestate.length(); i < length; i++) {
      char c = tracestate.charAt(i);
      // OWS is zero or more spaces or tabs https://httpwg.org/specs/rfc7230.html#rfc.section.3.2
      if (c == ' ' || c == '\t') continue; // trim whitespace
      if (c == '=') { // we reached a field name
        if (++i == length) break; // skip '=' character
        if (currentString.indexOf(key) == 0) {
          op = Op.THIS_ENTRY;
        } else {
          op = Op.OTHER_ENTRIES;
          if (otherEntries == null) otherEntries = new StringBuilder();
          otherEntries.append(',').append(currentString);
        }
        currentString.setLength(0);
      } else {
        currentString.append(c);
        continue;
      }
      // no longer whitespace
      switch (op) {
        case OTHER_ENTRIES:
          otherEntries.append(c);
          while (i < length && (c = tracestate.charAt(i)) != ',') {
            otherEntries.append(c);
            i++;
          }
          break;
        case THIS_ENTRY:
          int nextComma = tracestate.indexOf(',', i);
          int endIndex = nextComma != -1 ? nextComma : length;
          if (!handler.onThisEntry(tracestate, i, endIndex)) {
            break OUTER;
          }
          i = endIndex;
          break;
      }
    }
    return otherEntries;
  }

  // Simplify other rules by allowing value-based lookup on an ASCII value
  static boolean[] VALID_KEY_CHARS = new boolean[128];

  static {
    for (char c = 0; c < 128; c++) {
      VALID_KEY_CHARS[c] = isValidTracestateKeyChar(c);
    }
  }

  static boolean isValidTracestateKeyChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
      || c == '@' || c == '_' || c == '-' || c == '*' || c == '/';
  }

  // TODO: add logging etc.
  static boolean validateKey(CharSequence key) {
    int length = key.length();
    if (length == 0 || length > 256) return false;

    // Until we scan the entire key, we can't validate the first character, because the rules are
    // different depending on if there is an '@' or not.
    int vendorIndex = -1;
    for (int i = 0; i < length; i++) {
      char c = key.charAt(i);

      // The only valid characters are plain ASCII (numeric range 0-127)
      if (c > 128 || !VALID_KEY_CHARS[c]) return false;

      if (c == '@') {
        if (vendorIndex != -1) return false;
        vendorIndex = i;

        // must be at least one character after the tenant
        if (i++ == length) return false;
        if (key.charAt(i) < 'a') return false;
      }
    }

    // Now, go back and check to see if this was a Tenant formatted key, as the rules are different.
    // Either way, we already checked the boundary cases.
    char first = key.charAt(0);
    if (vendorIndex == -1) return first >= 'a';

    // tenant ID can only be up to 14 characters, but unlike vendor, it can start with a number.
    return length - vendorIndex <= 14 && first >= 'a' || first <= '9';
  }
}
