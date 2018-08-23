package brave.propagation;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.logging.Logger;

import static brave.internal.HexCodec.writeHexLong;
import static brave.internal.TraceContexts.FLAG_DEBUG;
import static brave.internal.TraceContexts.FLAG_SAMPLED;
import static brave.internal.TraceContexts.FLAG_SAMPLED_SET;
import static java.util.logging.Level.FINE;

/**
 * This format corresponds to the propagation key "b3" (or "B3"), which delimits fields in the
 * following manner.
 *
 * <pre>{@code
 * b3: {x-b3-traceid}-{x-b3-spanid}-{x-b3-sampled}-{x-b3-parentspanid}-{x-b3-flags}
 * }</pre>
 *
 * <p>For example, a sampled root span would look like:
 * {@code 4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-1}
 *
 * <p>Like normal B3, it is valid to omit trace identifiers in order to only propagate a sampling
 * decision. For example, the following are valid downstream hints:
 * <ul>
 * <li>sampled - {@code b3: 1}</li>
 * <li>unsampled - {@code b3: 0}</li>
 * <li>debug - {@code b3: 1-1}</li>
 * </ul>
 * Note: {@code b3: 0-1} isn't supported as it doesn't make sense. Debug boosts ordinary sampling
 * decision to also affect the collector tier. {@code b3: 0-1} would be like saying, don't sample,
 * except at the collector tier, which is impossible as if you don't sample locally the data will
 * never arrive at a collector.
 *
 * <p>See <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>
 */
public final class B3SingleFormat {
  static final Logger logger = Logger.getLogger(B3SingleFormat.class.getName());
  static final int FORMAT_MAX_LENGTH = 32 + 1 + 16 + 2 + 16 + 2; // traceid128-spanid-1-parentid-1

  /**
   * Writes all B3 defined fields in the trace context, except {@link TraceContext#parentIdAsLong()
   * parent ID}, to a hyphen delimited string.
   *
   * <p>This is appropriate for receivers who understand "b3" single header format, and always do
   * work in a child span. For example, message consumers always do work in child spans, so message
   * producers can use this format to save bytes on the wire. On the other hand, RPC clients should
   * use {@link #writeB3SingleFormat(TraceContext)} instead, as RPC servers often share a trace ID.
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
   * an RPC server. When downstream is known to be a messaging consumer, or a server that does not
   * share trace IDs, prefer {@link #writeB3SingleFormatWithoutParentId(TraceContext)}.
   */
  public static String writeB3SingleFormat(TraceContext context) {
    char[] buffer = getCharBuffer();
    int length = writeB3SingleFormat(context, context.parentIdAsLong(), buffer);
    return new String(buffer, 0, length);
  }

  /**
   * Like {@link #writeB3SingleFormatAsBytes(TraceContext)}, but for carriers with byte array or
   * byte buffer values. For example, {@link ByteBuffer#wrap(byte[])} can wrap the result.
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
      result[pos++] = sampled ? '1' : '0';
    }

    if (parentId != 0) {
      result[pos++] = '-';
      writeHexLong(result, pos, parentId);
      pos += 16;
    }

    if (context.debug()) {
      result[pos++] = '-';
      result[pos++] = '1';
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
    if (endIndex <= beginIndex + 3) { // possibly sampling flags
      return decode(b3, beginIndex, endIndex);
    }

    int pos = beginIndex;
    // At this point we minimally expect a traceId-spanId pair
    if (endIndex < 16 + 1 + 16 /* traceid64-spanid */) {
      logger.fine("Invalid input: truncated");
      return null;
    } else if (endIndex > FORMAT_MAX_LENGTH) {
      logger.fine("Invalid input: too long");
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
    if (!checkHyphen(b3, pos++)) return null;

    if (traceIdHigh == 0L && traceId == 0L) {
      logger.fine("Invalid input: expected a 16 or 32 lower hex trace ID at offset 0");
      return null;
    }

    long spanId = tryParse16HexCharacters(b3, pos, endIndex);
    if (spanId == 0L) {
      logger.log(FINE, "Invalid input: expected a 16 lower hex span ID at offset {0}", pos);
      return null;
    }
    pos += 16; // spanid

    int flags = 0;
    long parentId = 0L;
    if (endIndex > pos) {
      // If we are at this point, we have more than just traceId-spanId.
      // If the sampling field is present, we'll have a delimiter 2 characters from now. Ex "-1"
      // If it is absent, but a parent ID is (which is strange), we'll have at least 17 characters.
      // Therefore, if we have less than two characters, the input is truncated.
      if (endIndex == pos + 1) {
        logger.fine("Invalid input: truncated");
        return null;
      }
      if (!checkHyphen(b3, pos++)) return null;

      // If our position is at the end of the string, or another delimiter is one character past our
      // position, try to read sampled status.
      if (endIndex == pos + 1 || delimiterFollowsPos(b3, pos, endIndex)) {
        flags = parseSampledFlag(b3, pos);
        if (flags == 0) return null;
        pos++; // consume the sampled status
      }

      if (endIndex > pos) {
        // If we are at this point, we have only two possible fields left, parent and/or debug
        // If the parent field is present, we'll have at least 17 characters. If it is absent, but debug
        // is present, we'll have we'll have a delimiter 2 characters from now. Ex "-1"
        // Therefore, if we have less than two characters, the input is truncated.
        if (endIndex == pos + 1) {
          logger.fine("Invalid input: truncated");
          return null;
        }

        if (endIndex > pos + 2) {
          if (!checkHyphen(b3, pos++)) return null;
          parentId = tryParse16HexCharacters(b3, pos, endIndex);
          if (parentId == 0L) {
            logger.log(FINE, "Invalid input: expected a 16 lower hex parent ID at offset {0}", pos);
            return null;
          }
          pos += 16;
        }

        // the only option at this point is that we have a debug flag
        if (endIndex == pos + 2) {
          if (!checkHyphen(b3, pos)) return null;
          pos++; // consume the hyphen
          flags = parseDebugFlag(b3, pos, flags);
          if (flags == 0) return null;
        }
      }
    }

    return TraceContextOrSamplingFlags.create(new TraceContext(
        flags,
        traceIdHigh,
        traceId,
        parentId,
        spanId,
        Collections.emptyList()
    ));
  }

  @Nullable
  static TraceContextOrSamplingFlags decode(CharSequence b3, int beginIndex, int endIndex) {
    int pos = beginIndex;
    if (pos == endIndex) { // empty
      logger.log(FINE, "Invalid input: expected 0 or 1 for sampled at offset {0}", pos);
      return null;
    }

    int flags = parseSampledFlag(b3, pos++);
    if (flags == 0) return null;
    if (endIndex > pos) {
      if (!checkHyphen(b3, pos++)) return null;
      if (endIndex == pos) {
        logger.fine("Invalid input: truncated");
        return null;
      }
      flags = parseDebugFlag(b3, pos, flags);
      if (flags == 0) return null;
    }
    return TraceContextOrSamplingFlags.create(SamplingFlags.toSamplingFlags(flags));
  }

  static boolean checkHyphen(CharSequence b3, int pos) {
    if (b3.charAt(pos) == '-') return true;
    logger.log(FINE, "Invalid input: expected a hyphen(-) delimiter offset {0}", pos);
    return false;
  }

  static boolean delimiterFollowsPos(CharSequence b3, int pos, int end) {
    return (end >= pos + 2) && b3.charAt(pos + 1) == '-';
  }

  static long tryParse16HexCharacters(CharSequence lowerHex, int index, int end) {
    int endIndex = index + 16;
    if (endIndex > end) return 0L;
    return HexCodec.lenientLowerHexToUnsignedLong(lowerHex, index, endIndex);
  }

  static int parseSampledFlag(CharSequence b3, int pos) {
    int flags;
    char sampledChar = b3.charAt(pos);
    if (sampledChar == '1') {
      flags = FLAG_SAMPLED_SET | FLAG_SAMPLED;
    } else if (sampledChar == '0') {
      flags = FLAG_SAMPLED_SET;
    } else {
      logger.log(FINE, "Invalid input: expected 0 or 1 for sampled at offset {0}", pos);
      flags = 0;
    }
    return flags;
  }

  static int parseDebugFlag(CharSequence b3, int pos, int flags) {
    char lastChar = b3.charAt(pos);
    if (lastChar == '1') {
      flags = FLAG_DEBUG | FLAG_SAMPLED_SET | FLAG_SAMPLED;
    } else if (lastChar != '0') { // redundant to say debug false, but whatev
      logger.log(FINE, "Invalid input: expected 1 for debug at offset {0}", pos);
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
