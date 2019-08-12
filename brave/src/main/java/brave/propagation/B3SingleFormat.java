/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package brave.propagation;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.internal.Platform;
import java.nio.ByteBuffer;
import java.util.Collections;

import static brave.internal.HexCodec.writeHexLong;
import static brave.internal.InternalPropagation.FLAG_DEBUG;
import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;

/**
 * This format corresponds to the propagation key "b3" (or "B3"), which delimits fields in the
 * following manner.
 *
 * <pre>{@code
 * b3: {x-b3-traceid}-{x-b3-spanid}-{if x-b3-flags 'd' else x-b3-sampled}-{x-b3-parentspanid}
 * }</pre>
 *
 * <p>For example, a sampled root span would look like:
 * {@code 4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1}
 *
 * <p>... a not yet sampled root span would look like:
 * {@code 4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7}
 *
 * <p>... and a debug RPC child span would look like:
 * {@code 4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-d-5b4185666d50f68b}
 *
 * <p>Like normal B3, it is valid to omit trace identifiers in order to only propagate a sampling
 * decision. For example, the following are valid downstream hints:
 * <ul>
 * <li>don't sample - {@code b3: 0}</li>
 * <li>sampled - {@code b3: 1}</li>
 * <li>debug - {@code b3: d}</li>
 * </ul>
 *
 * Reminder: debug (previously {@code X-B3-Flags: 1}), is a boosted sample signal which is recorded
 * to ensure it reaches the collector tier. See {@link TraceContext#debug()}.
 *
 * <p>See <a href="https://github.com/apache/incubator-zipkin-b3-propagation">B3 Propagation</a>
 */
public final class B3SingleFormat {
  static final int FORMAT_MAX_LENGTH = 32 + 1 + 16 + 3 + 16; // traceid128-spanid-1-parentid

  /**
   * Writes all B3 defined fields in the trace context, except {@link TraceContext#parentIdAsLong()
   * parent ID}, to a hyphen delimited string.
   *
   * <p>This is appropriate for receivers who understand "b3" single header format, and always do
   * work in a child span. For example, message consumers always do work in child spans, so message
   * producers can use this format to save bytes on the wire. On the other hand, RPC clients should
   * use {@link #writeB3SingleFormat(TraceContext)} instead, as RPC servers often share a span ID
   * with the client.
   */
  public static String writeB3SingleFormatWithoutParentId(TraceContext context) {
    char[] buffer = getCharBuffer();
    int length = writeB3SingleFormat(context, 0L, buffer);
    return new String(buffer, 0, length);
  }

  /**
   * Like {@link #writeB3SingleFormatWithoutParentId(TraceContext)}, but for carriers with byte
   * array or byte buffer values. For example, {@link ByteBuffer#wrap(byte[])} can wrap the result.
   */
  public static byte[] writeB3SingleFormatWithoutParentIdAsBytes(TraceContext context) {
    char[] buffer = getCharBuffer();
    int length = writeB3SingleFormat(context, 0L, buffer);
    return asciiToNewByteArray(buffer, length);
  }

  /**
   * Writes all B3 defined fields in the trace context to a hyphen delimited string. This is
   * appropriate for receivers who understand "b3" single header format.
   *
   * <p>The {@link TraceContext#parentIdAsLong() parent ID} is serialized in case the receiver is
   * an RPC server. When downstream is known to be a messaging consumer, or a server that never
   * reuses a client's span ID, prefer {@link #writeB3SingleFormatWithoutParentId(TraceContext)}.
   */
  public static String writeB3SingleFormat(TraceContext context) {
    char[] buffer = getCharBuffer();
    int length = writeB3SingleFormat(context, context.parentIdAsLong(), buffer);
    return new String(buffer, 0, length);
  }

  /**
   * Like {@link #writeB3SingleFormat(TraceContext)}, but for carriers with byte array or byte
   * buffer values. For example, {@link ByteBuffer#wrap(byte[])} can wrap the result.
   */
  public static byte[] writeB3SingleFormatAsBytes(TraceContext context) {
    char[] buffer = getCharBuffer();
    int length = writeB3SingleFormat(context, context.parentIdAsLong(), buffer);
    return asciiToNewByteArray(buffer, length);
  }

  static int writeB3SingleFormat(TraceContext context, long parentId, char[] result) {
    int pos = 0;
    long traceIdHigh = context.traceIdHigh();
    if (traceIdHigh != 0L) {
      writeHexLong(result, pos, traceIdHigh);
      pos += 16;
    }
    writeHexLong(result, pos, context.traceId());
    pos += 16;
    result[pos++] = '-';
    writeHexLong(result, pos, context.spanId());
    pos += 16;

    Boolean sampled = context.sampled();
    if (sampled != null) {
      result[pos++] = '-';
      result[pos++] = context.debug() ? 'd' : sampled ? '1' : '0';
    }

    if (parentId != 0L) {
      result[pos++] = '-';
      writeHexLong(result, pos, parentId);
      pos += 16;
    }
    return pos;
  }

  @Nullable
  public static TraceContextOrSamplingFlags parseB3SingleFormat(CharSequence b3) {
    return parseB3SingleFormat(b3, 0, b3.length());
  }

  /**
   * @param beginIndex the start index, inclusive
   * @param endIndex the end index, exclusive
   */
  @Nullable
  public static TraceContextOrSamplingFlags parseB3SingleFormat(CharSequence b3, int beginIndex,
    int endIndex) {
    if (beginIndex == endIndex) {
      Platform.get().log("Invalid input: empty", null);
      return null;
    }

    int pos = beginIndex;
    if (pos + 1 == endIndex) { // possibly sampling flags
      return tryParseSamplingFlags(b3, pos);
    }

    // At this point we minimally expect a traceId-spanId pair
    if (endIndex < 16 + 1 + 16 /* traceid64-spanid */) {
      Platform.get().log("Invalid input: truncated", null);
      return null;
    } else if (endIndex > FORMAT_MAX_LENGTH) {
      Platform.get().log("Invalid input: too long", null);
      return null;
    }

    long traceIdHigh, traceId;
    if (b3.charAt(pos + 32) == '-') {
      traceIdHigh = tryParse16HexCharacters(b3, pos, endIndex);
      pos += 16; // upper 64 bits of the trace ID
      traceId = tryParse16HexCharacters(b3, pos, endIndex);
    } else {
      traceIdHigh = 0L;
      traceId = tryParse16HexCharacters(b3, pos, endIndex);
    }
    pos += 16; // traceId

    if (traceIdHigh == 0L && traceId == 0L) {
      Platform.get().log("Invalid input: expected a 16 or 32 lower hex trace ID at offset 0", null);
      return null;
    }

    if (isLowerHex(b3.charAt(pos))) {
      Platform.get().log("Invalid input: trace ID is too long", null);
      return null;
    }

    if (!checkHyphen(b3, pos++)) return null;

    long spanId = tryParse16HexCharacters(b3, pos, endIndex);
    if (spanId == 0L) {
      Platform.get().log("Invalid input: expected a 16 lower hex span ID at offset {0}", pos, null);
      return null;
    }
    pos += 16; // spanid

    int flags = 0;
    long parentId = 0L;
    if (endIndex > pos) {
      if (isLowerHex(b3.charAt(pos))) {
        Platform.get().log("Invalid input: span ID is too long", null);
        return null;
      }

      // If we are at this point, we have more than just traceId-spanId.
      // If the sampling field is present, we'll have a delimiter 2 characters from now. Ex "-1"
      // If it is absent, but a parent ID is (which is strange), we'll have at least 17 characters.
      // Therefore, if we have less than two characters, the input is truncated.
      if (endIndex == pos + 1) {
        Platform.get().log("Invalid input: truncated", null);
        return null;
      }
      if (!checkHyphen(b3, pos++)) return null;

      // If our position is at the end of the string, or another delimiter is one character past our
      // position, try to read sampled status.
      boolean afterSampledField = notHexFollowsPos(b3, pos, endIndex);
      if (endIndex == pos + 1 || afterSampledField) {
        flags = parseFlags(b3, pos);
        if (flags == 0) return null;
        pos++; // consume the sampled status
        if (afterSampledField && !checkHyphen(b3, pos++)) return null; // consume the delimiter
      }

      if (endIndex > pos || afterSampledField) {
        // If we are at this point, we should have a parent ID, encoded as "[0-9a-f]{16}"
        parentId = tryParseParentId(b3, pos, endIndex);
        if (parentId == 0L) return null;
      }
    }

    return TraceContextOrSamplingFlags.create(new TraceContext(
      flags,
      traceIdHigh,
      traceId,
      0L, // localRootId is the first ID used in process, not necessarily the one extracted
      parentId,
      spanId,
      Collections.emptyList()
    ));
  }

  /** Returns zero if truncated, malformed, or too big after logging */
  static long tryParseParentId(CharSequence b3, int pos, int endIndex) {
    if (endIndex < pos + 16) {
      Platform.get().log("Invalid input: truncated", null);
      return 0L;
    }

    long parentId = tryParse16HexCharacters(b3, pos, endIndex);
    if (parentId == 0L) {
      Platform.get()
        .log("Invalid input: expected a 16 lower hex parent ID at offset {0}", pos, null);
      return 0L;
    }

    pos += 16;
    if (endIndex != pos) {
      Platform.get().log("Invalid input: parent ID is too long", null);
      return 0L;
    }
    return parentId;
  }

  static TraceContextOrSamplingFlags tryParseSamplingFlags(CharSequence b3, int pos) {
    int flags = parseFlags(b3, pos);
    if (flags == 0) return null;
    return TraceContextOrSamplingFlags.create(SamplingFlags.toSamplingFlags(flags));
  }

  static boolean checkHyphen(CharSequence b3, int pos) {
    if (b3.charAt(pos) == '-') return true;
    Platform.get().log("Invalid input: expected a hyphen(-) delimiter at offset {0}", pos, null);
    return false;
  }

  static boolean notHexFollowsPos(CharSequence b3, int pos, int end) {
    return (end >= pos + 2) && !isLowerHex(b3.charAt(pos + 1));
  }

  static long tryParse16HexCharacters(CharSequence lowerHex, int index, int end) {
    int endIndex = index + 16;
    if (endIndex > end) return 0L;
    return HexCodec.lenientLowerHexToUnsignedLong(lowerHex, index, endIndex);
  }

  static int parseFlags(CharSequence b3, int pos) {
    int flags;
    char sampledChar = b3.charAt(pos);
    if (sampledChar == 'd') {
      flags = FLAG_SAMPLED_SET | FLAG_SAMPLED | FLAG_DEBUG;
    } else if (sampledChar == '1') {
      flags = FLAG_SAMPLED_SET | FLAG_SAMPLED;
    } else if (sampledChar == '0') {
      flags = FLAG_SAMPLED_SET;
    } else {
      logInvalidSampled(pos);
      flags = 0;
    }
    return flags;
  }

  static void logInvalidSampled(int pos) {
    Platform.get().log("Invalid input: expected 0, 1 or d for sampled at offset {0}", pos, null);
  }

  static byte[] asciiToNewByteArray(char[] buffer, int length) {
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = (byte) buffer[i];
    }
    return result;
  }

  static final ThreadLocal<char[]> CHAR_BUFFER = new ThreadLocal<>();

  static char[] getCharBuffer() {
    char[] charBuffer = CHAR_BUFFER.get();
    if (charBuffer == null) {
      charBuffer = new char[FORMAT_MAX_LENGTH];
      CHAR_BUFFER.set(charBuffer);
    }
    return charBuffer;
  }

  static boolean isLowerHex(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
  }

  B3SingleFormat() {
  }
}
