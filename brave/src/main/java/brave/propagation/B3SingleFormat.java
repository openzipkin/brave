package brave.propagation;

import brave.internal.Nullable;
import java.util.Collections;
import java.util.logging.Logger;

import static brave.internal.HexCodec.lenientLowerHexToUnsignedLong;
import static brave.internal.HexCodec.writeHexLong;
import static brave.internal.TraceContexts.FLAG_DEBUG;
import static brave.internal.TraceContexts.FLAG_SAMPLED;
import static brave.internal.TraceContexts.FLAG_SAMPLED_SET;
import static java.util.logging.Level.FINE;

/** Implements the progation format described in {@link B3SinglePropagation}. */
final class B3SingleFormat {
  static final Logger logger = Logger.getLogger(B3SingleFormat.class.getName());
  static final int FORMAT_MAX_LENGTH = 32 + 1 + 16 + 2 + 16 + 2; // traceid128-spanid-1-parentid-1

  static String writeB3SingleFormat(TraceContext context) {
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

    long b3Id = context.parentIdAsLong();
    if (b3Id != 0) {
      result[pos++] = '-';
      writeHexLong(result, pos, b3Id);
      pos += 16;
    }

    if (context.debug()) {
      result[pos++] = '-';
      result[pos++] = '1';
    }
    return new String(result, 0, pos);
  }

  static @Nullable TraceContextOrSamplingFlags maybeB3SingleFormat(String b3) {
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

    long traceIdHigh, traceId;
    boolean traceId128 = b3.charAt(32) == '-';
    if (traceId128) {
      traceIdHigh = lenientLowerHexToUnsignedLong(b3, 0, 16);
      traceId = lenientLowerHexToUnsignedLong(b3, 16, 32);
    } else {
      traceIdHigh = 0L;
      traceId = lenientLowerHexToUnsignedLong(b3, 0, 16);
    }

    if (traceIdHigh == 0L && traceId == 0L) {
      logger.fine("Invalid input: expected a 16 or 32 lower hex trace ID at offset 0");
      return null;
    }

    int pos = traceId128 ? 33 : 17; // traceid-
    long spanId = lenientLowerHexToUnsignedLong(b3, pos, pos + 16);
    if (spanId == 0L) {
      logger.log(FINE, "Invalid input: expected a 16 lower hex span ID at offset {0}", pos);
      return null;
    }
    pos += 17; // spanid-

    int flags = 0;
    if (length == pos + 1 || (length > pos + 2 && b3.charAt(pos + 1) == '-')) {
      flags = parseSampledFlag(b3, pos++);
      if (flags == 0) return null;
      pos++; // skip the dash
    }

    long parentId = 0L;
    if (length >= pos + 17) {
      parentId = lenientLowerHexToUnsignedLong(b3, pos, pos + 16);
      if (parentId == 0L) {
        logger.log(FINE, "Invalid input: expected a 16 lower hex parent ID at offset {0}", pos);
        return null;
      }
      pos += 17;
    }

    if (length == pos + 1) {
      flags = parseDebugFlag(b3, pos, flags);
      if (flags == 0) return null;
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
