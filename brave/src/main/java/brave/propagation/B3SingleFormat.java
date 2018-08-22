package brave.propagation;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import java.util.Collections;
import java.util.logging.Logger;

import static brave.internal.HexCodec.writeHexLong;
import static brave.internal.TraceContexts.FLAG_DEBUG;
import static brave.internal.TraceContexts.FLAG_SAMPLED;
import static brave.internal.TraceContexts.FLAG_SAMPLED_SET;
import static java.util.logging.Level.FINE;

/** Implements the propagation format described in {@link B3SinglePropagation}. */
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
    return writeB3SingleFormat(context, 0L);
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
    return writeB3SingleFormat(context, context.parentIdAsLong());
  }

  static String writeB3SingleFormat(TraceContext context, long parentId) {
    char[] result = getCharBuffer();
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
    return new String(result, 0, pos);
  }

  @Nullable public static TraceContextOrSamplingFlags parseB3SingleFormat(String b3) {
    int length = b3.length();
    if (length == 1) { // assume just tracing flag. ex "b3: 1"
      int flags = parseSampledFlag(b3, 0);
      if (flags == 0) return null;
      return TraceContextOrSamplingFlags.create(SamplingFlags.toSamplingFlags(flags));
    } else if (length == 3) { // assume tracing + debug flag. ex "b3: 1-1"
      int flags = parseSampledFlag(b3, 0);
      if (flags == 0) return null;
      flags = parseDebugFlag(b3, 2, flags);
      if (flags == 0) return null;
      return TraceContextOrSamplingFlags.create(SamplingFlags.toSamplingFlags(flags));
    }

    // At this point we minimally expect a traceId-spanId pair
    if (length < 16 + 1 + 16 /* traceid64-spanid */) {
      logger.fine("Invalid input: truncated");
      return null;
    } else if (length > FORMAT_MAX_LENGTH) {
      logger.fine("Invalid input: too long");
      return null;
    }

    int pos = 0;
    long traceIdHigh, traceId;
    if (b3.charAt(32) == '-') {
      traceIdHigh = tryParse16HexCharacters(b3, pos, length);
      pos += 16; // upper 64 bits of the trace ID
      traceId = tryParse16HexCharacters(b3, pos, length);
    } else {
      traceIdHigh = 0L;
      traceId = tryParse16HexCharacters(b3, pos, length);
    }
    pos += 16; // traceId
    if (!checkHyphen(b3, pos)) return null;
    pos++; // consume the hyphen

    if (traceIdHigh == 0L && traceId == 0L) {
      logger.fine("Invalid input: expected a 16 or 32 lower hex trace ID at offset 0");
      return null;
    }

    long spanId = tryParse16HexCharacters(b3, pos, length);
    if (spanId == 0L) {
      logger.log(FINE, "Invalid input: expected a 16 lower hex span ID at offset {0}", pos);
      return null;
    }
    pos += 16; // spanid

    int flags = 0;
    long parentId = 0L;
    if (length > pos) {
      // If we are at this point, we have more than just traceId-spanId.
      // If the sampling field is present, we'll have a delimiter 2 characters from now. Ex "-1"
      // If it is absent, but a parent ID is (which is strange), we'll have at least 17 characters.
      // Therefore, if we have less than two characters, the input is truncated.
      if (length == pos + 1) {
        logger.fine("Invalid input: truncated");
        return null;
      }
      if (!checkHyphen(b3, pos)) return null;
      pos++; // consume the hyphen

      // If our position is at the end of the string, or another delimiter is one character past our
      // position, try to read sampled status.
      if (length == pos + 1 || delimiterFollowsPos(b3, pos, length)) {
        flags = parseSampledFlag(b3, pos);
        if (flags == 0) return null;
        pos++; // consume the sampled status
      }

      if (length > pos) {
        // If we are at this point, we have only two possible fields left, parent and/or debug
        // If the parent field is present, we'll have at least 17 characters. If it is absent, but debug
        // is present, we'll have we'll have a delimiter 2 characters from now. Ex "-1"
        // Therefore, if we have less than two characters, the input is truncated.
        if (length == pos + 1) {
          logger.fine("Invalid input: truncated");
          return null;
        }

        if (length > pos + 2) {
          if (!checkHyphen(b3, pos)) return null;
          pos++; // consume the hyphen
          parentId = tryParse16HexCharacters(b3, pos, length);
          if (parentId == 0L) {
            logger.log(FINE, "Invalid input: expected a 16 lower hex parent ID at offset {0}", pos);
            return null;
          }
          pos += 16;
        }

        // the only option at this point is that we have a debug flag
        if (length == pos + 2) {
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

  static boolean checkHyphen(String b3, int pos) {
    if (b3.charAt(pos) == '-') return true;
    logger.log(FINE, "Invalid input: expected a hyphen(-) delimiter offset {0}", pos);
    return false;
  }

  static boolean delimiterFollowsPos(String b3, int pos, int length) {
    return (length >= pos + 2) && b3.charAt(pos + 1) == '-';
  }

  static long tryParse16HexCharacters(CharSequence lowerHex, int index, int length) {
    int endIndex = index + 16;
    if (endIndex > length) return 0L;
    return HexCodec.lenientLowerHexToUnsignedLong(lowerHex, index, endIndex);
  }

  static int parseSampledFlag(String b3, int pos) {
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

  static int parseDebugFlag(String b3, int pos, int flags) {
    char lastChar = b3.charAt(pos);
    if (lastChar == '1') {
      flags = FLAG_DEBUG | FLAG_SAMPLED_SET | FLAG_SAMPLED;
    } else if (lastChar != '0') { // redundant to say debug false, but whatev
      logger.log(FINE, "Invalid input: expected 1 for debug at offset {0}", pos);
      flags = 0;
    }
    return flags;
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
}
