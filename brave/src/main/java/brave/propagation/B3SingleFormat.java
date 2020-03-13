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
package brave.propagation;

import brave.internal.Nullable;
import brave.internal.Platform;
import java.nio.ByteBuffer;
import java.util.Collections;

import static brave.internal.HexCodec.lenientLowerHexToUnsignedLong;
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
 * <p>See <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>
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
   * This reads a trace context a sequence potentially larger than the format. The use-case is
   * reducing garbage, by re-using the input {@code value} across multiple parse operations.
   *
   * @param value the sequence that contains a B3 single formatted trace context
   * @param beginIndex the inclusive begin index: {@linkplain CharSequence#charAt(int) index} of the
   * first character in B3 single format.
   * @param endIndex the exclusive end index: {@linkplain CharSequence#charAt(int) index}
   * <em>after</em> the last character in B3 single format.
   */
  @Nullable
  public static TraceContextOrSamplingFlags parseB3SingleFormat(CharSequence value, int beginIndex,
    int endIndex) {
    int length = endIndex - beginIndex;

    if (length == 0) {
      Platform.get().log("Invalid input: empty", null);
      return null;
    } else if (length == 1) { // possibly sampling flags
      return tryParseSamplingFlags(value.charAt(beginIndex));
    } else if (length > FORMAT_MAX_LENGTH) {
      Platform.get().log("Invalid input: too long", null);
      return null;
    }

    // This initial scan helps simplify other parsing logic by constraining the amount of characters
    // they need to consider, and if there are not enough or too many fields.
    int hyphenCount = 0;
    int indexOfFirstHyphen = -1;
    for (int i = beginIndex; i < endIndex; i++) {
      char c = value.charAt(i);
      if (c == '-') {
        if (indexOfFirstHyphen == -1) {
          indexOfFirstHyphen = i;
        }
        hyphenCount++;
      } else if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
        Platform.get().log("Invalid input: only valid characters are lower-hex and hyphen", null);
        return null;
      }
    }

    if (indexOfFirstHyphen == -1) {
      Platform.get().log("Truncated reading trace ID", null);
      return null;
    } else if (hyphenCount > 3) {
      Platform.get().log("Invalid input: more than 4 fields exist", null);
      return null;
    }

    int pos = beginIndex;

    long traceIdHigh, traceId;
    int traceIdLength = indexOfFirstHyphen - beginIndex;
    if (traceIdLength == 32) {
      traceIdHigh = lenientLowerHexToUnsignedLong(value, pos, pos + 16);
      pos += 16; // upper 64 bits of the trace ID
    } else if (traceIdLength == 16) {
      traceIdHigh = 0L;
    } else {
      Platform.get().log("Invalid input: expected a 16 or 32 lower hex trace ID", null);
      return null;
    }

    traceId = tryParseHex("trace ID", value, pos, endIndex);
    if (traceId == 0) return null;
    pos += 17; // lower 64 bits of the trace ID and the hyphen

    long spanId = tryParseHex("span ID", value, pos, endIndex);
    if (spanId == 0) return null;
    pos += 16; // spanid

    int flags = 0;
    long parentId = 0L;
    if (hyphenCount > 1) { // traceid-spanid-
      pos++; // consume the hyphen

      if (hyphenCount == 3) { // we should parse sampled AND parent ID
        flags = tryParseSampledFlags(value, endIndex, pos);
        if (flags == 0) return null;
        pos += 2; // consume the sampled flag and hyphen
        parentId = tryParseParentId(value, endIndex, pos);
        if (parentId == 0L) return null;
      } else { // we should parse sampled OR parent ID
        if (endIndex - pos <= 1) {
          flags = tryParseSampledFlags(value, endIndex, pos);
          if (flags == 0) return null;
        } else {
          parentId = tryParseParentId(value, endIndex, pos);
          if (parentId == 0L) return null;
        }
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

  static int tryParseSampledFlags(CharSequence value, int endIndex, int pos) {
    if (!validateFieldLength("sampled", 1, value, pos, endIndex)) return 0;
    return parseSampledFlags(value.charAt(pos));
  }

  static long tryParseParentId(CharSequence value, int endIndex, int pos) {
    return tryParseHex("parent ID", value, pos, endIndex);
  }

  /** Returns zero if truncated, malformed, or too big after logging */
  static long tryParseHex(String name, CharSequence value, int beginIndex, int endIndex) {
    if (!validateFieldLength(name, 16, value, beginIndex, endIndex)) return 0L;

    long id = lenientLowerHexToUnsignedLong(value, beginIndex, beginIndex + 16);
    if (id != 0L) return id;

    // If we got here, we either read 16 zeroes or a mix of hyphens and hex.
    Platform.get().log("Invalid input: expected a lower hex {0}", name, null);
    return 0L;
  }

  static boolean validateFieldLength(String name, int length, CharSequence value, int beginIndex,
    int endIndex) {
    int endOfId = beginIndex + length;
    if (beginIndex == endIndex || value.charAt(beginIndex) == '-') {
      Platform.get().log("Invalid input: empty {0}", name, null);
      return false;
    } else if (endIndex < endOfId) {
      Platform.get().log("Truncated reading {0}", name, null);
      return false;
    } else if (endIndex > endOfId && value.charAt(endOfId) != '-') {
      Platform.get().log("Invalid input: {0} is too long", name, null);
      return false;
    }
    return true;
  }

  static TraceContextOrSamplingFlags tryParseSamplingFlags(char sampledChar) {
    int flags = parseSampledFlags(sampledChar);
    if (flags == 0) return null;
    return TraceContextOrSamplingFlags.create(SamplingFlags.toSamplingFlags(flags));
  }

  static int parseSampledFlags(char sampledChar) {
    int flags;
    if (sampledChar == 'd') {
      flags = FLAG_SAMPLED_SET | FLAG_SAMPLED | FLAG_DEBUG;
    } else if (sampledChar == '1') {
      flags = FLAG_SAMPLED_SET | FLAG_SAMPLED;
    } else if (sampledChar == '0') {
      flags = FLAG_SAMPLED_SET;
    } else {
      Platform.get().log("Invalid input: expected 0, 1 or d for sampled", null);
      flags = 0;
    }
    return flags;
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

  B3SingleFormat() {
  }
}
