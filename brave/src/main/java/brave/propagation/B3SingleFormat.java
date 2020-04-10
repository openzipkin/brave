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
import brave.internal.RecyclableBuffers;
import java.nio.ByteBuffer;
import java.util.Collections;

import static brave.internal.HexCodec.writeHexLong;

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

  static final int // instead of enum for smaller bytecode
    FIELD_TRACE_ID_HIGH = 1,
    FIELD_TRACE_ID = 2,
    FIELD_SPAN_ID = 3,
    FIELD_SAMPLED = 4,
    FIELD_PARENT_SPAN_ID = 5;

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
    char[] buffer = RecyclableBuffers.parseBuffer();
    int length = writeB3SingleFormat(context, 0L, buffer);
    return new String(buffer, 0, length);
  }

  /**
   * Like {@link #writeB3SingleFormatWithoutParentId(TraceContext)}, but for carriers with byte
   * array or byte buffer values. For example, {@link ByteBuffer#wrap(byte[])} can wrap the result.
   */
  public static byte[] writeB3SingleFormatWithoutParentIdAsBytes(TraceContext context) {
    char[] buffer = RecyclableBuffers.parseBuffer();
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
    char[] buffer = RecyclableBuffers.parseBuffer();
    int length = writeB3SingleFormat(context, context.parentIdAsLong(), buffer);
    return new String(buffer, 0, length);
  }

  /**
   * Like {@link #writeB3SingleFormat(TraceContext)}, but for carriers with byte array or byte
   * buffer values. For example, {@link ByteBuffer#wrap(byte[])} can wrap the result.
   */
  public static byte[] writeB3SingleFormatAsBytes(TraceContext context) {
    char[] buffer = RecyclableBuffers.parseBuffer();
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
      SamplingFlags flags = tryParseSamplingFlags(value.charAt(beginIndex));
      return flags != null ? TraceContextOrSamplingFlags.create(flags) : null;
    } else if (length > FORMAT_MAX_LENGTH) {
      Platform.get().log("Invalid input: too long", null);
      return null;
    }

    long traceIdHigh = 0L, traceId = 0L, spanId = 0L, parentId = 0L;
    int flags = 0;

    // Assume it is a 128-bit trace ID and revise back as necessary
    int currentField = FIELD_TRACE_ID_HIGH, currentFieldLength = 0;
    // Used for hex-decoding, performed by bitwise addition
    long buffer = 0L;

    // Instead of pos < endIndex, this uses pos <= endIndex to keep field processing consolidated.
    // Otherwise, we'd have to process again when outside the loop to handle dangling data on EOF.
    for (int pos = beginIndex; pos <= endIndex; pos++) {
      // treat EOF same as a hyphen for simplicity
      boolean isEof = pos == endIndex;
      char c = isEof ? '-' : value.charAt(pos);

      if (c == '-') {
        if (currentField == FIELD_SAMPLED) {
          // The last field could be sampled or parent ID. Revise assumption if longer than 1 char.
          if (isEof && currentFieldLength > 1) {
            currentField = FIELD_PARENT_SPAN_ID;
          }
        } else if (currentField == FIELD_TRACE_ID_HIGH) {
          // We reached a hyphen before the 17th hex character. This means it is a 64-bit trace ID.
          currentField = FIELD_TRACE_ID;
        }

        if (!validateFieldLength(currentField, currentFieldLength)) {
          return null;
        }

        switch (currentField) {
          case FIELD_TRACE_ID:
            traceId = buffer;

            currentField = FIELD_SPAN_ID;
            break;
          case FIELD_SPAN_ID:
            spanId = buffer;

            // To handle malformed cases like below, it is easier to assume the next field is
            // sampled and revert if later not vs peek to determine if it is sampled or parent ID.
            // 'traceId-spanId--parentSpanId'
            // 'traceId-spanId-'
            currentField = FIELD_SAMPLED;
            break;
          case FIELD_SAMPLED:
            SamplingFlags samplingFlags = tryParseSamplingFlags(value.charAt(pos - 1));
            if (samplingFlags == null) return null;
            flags = samplingFlags.flags;

            currentField = FIELD_PARENT_SPAN_ID;
            break;
          case FIELD_PARENT_SPAN_ID:
            parentId = buffer;

            if (!isEof) {
              Platform.get().log("Invalid input: more than 4 fields exist", null);
              return null;
            }

            break;
          default:
            throw new AssertionError();
        }

        buffer = 0L;
        currentFieldLength = 0;
        continue;
      }

      // At this point, 'c' is not a hyphen

      // When we get to a non-hyphen at position 16, we have a 128-bit trace ID.
      if (currentField == FIELD_TRACE_ID_HIGH && currentFieldLength == 16) {
        // No validation: traceIdHigh is all zeros when a 64-bit trace ID is encoded in 128-bits.
        traceIdHigh = buffer;

        // This character is the next hex. If it isn't, the next iteration will throw. Either way,
        // reset so that we can capture the next 16 characters of the trace ID.
        buffer = 0L;
        currentField = FIELD_TRACE_ID;
        currentFieldLength = 0;
      }

      currentFieldLength++;

      // The rest of this is normal lower-hex decoding
      buffer <<= 4;
      if (c >= '0' && c <= '9') {
        buffer |= c - '0';
      } else if (c >= 'a' && c <= 'f') {
        buffer |= c - 'a' + 10;
      } else {
        log(currentField, "Invalid input: only valid characters are lower-hex for {0}");
        return null;
      }
    }

    // Since we are using a hidden constructor, we need to validate here.
    if ((traceIdHigh == 0L && traceId == 0L) || spanId == 0L) {
      int field = spanId == 0L ? FIELD_SPAN_ID : FIELD_TRACE_ID;
      log(field, "Invalid input: read all zeros {0}");
      return null;
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

  @Nullable static SamplingFlags tryParseSamplingFlags(char sampledChar) {
    switch (sampledChar) {
      case '1':
        return SamplingFlags.SAMPLED;
      case '0':
        return SamplingFlags.NOT_SAMPLED;
      case 'd':
        return SamplingFlags.DEBUG;
      default:
        log(FIELD_SAMPLED, "Invalid input: expected 0, 1 or d for {0}");
        return null;
    }
  }

  static boolean validateFieldLength(int field, int length) {
    int expectedLength = field == FIELD_SAMPLED ? 1 : 16;
    if (length == 0) {
      log(field, "Invalid input: empty {0}");
      return false;
    } else if (length < expectedLength) {
      log(field, "Invalid input: {0} is too short");
      return false;
    } else if (length > expectedLength) {
      log(field, "Invalid input: {0} is too long");
      return false;
    }
    return true;
  }

  static void log(int fieldCode, String s) {
    String field;
    switch (fieldCode) {
      case FIELD_TRACE_ID_HIGH:
      case FIELD_TRACE_ID:
        field = "trace ID";
        break;
      case FIELD_SPAN_ID:
        field = "span ID";
        break;
      case FIELD_SAMPLED:
        field = "sampled";
        break;
      case FIELD_PARENT_SPAN_ID:
        field = "parent ID";
        break;
      default:
        throw new AssertionError("field code unmatched: " + fieldCode);
    }
    Platform.get().log(s, field, null);
  }

  static byte[] asciiToNewByteArray(char[] buffer, int length) {
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = (byte) buffer[i];
    }
    return result;
  }

  B3SingleFormat() {
  }
}
