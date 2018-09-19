package com.github.kristofa.brave.http;

import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.internal.Nullable;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.kristofa.brave.IdConversion.lenientConvertToLong;

/** Copied from Brave 5.3.3 and backported. */
public final class B3SingleFormat {
  static final Logger LOG = Logger.getLogger(HttpServerRequestAdapter.class.getName());
  static final int
      FLAG_SAMPLED = 1 << 1,
      FLAG_SAMPLED_SET = 1 << 2,
      FLAG_DEBUG = 1 << 3;
  static final int FORMAT_MAX_LENGTH = 32 + 1 + 16 + 2 + 16; // traceid128-spanid-1-parentid

  @Nullable
  public static TraceData parseB3SingleFormat(String b3) {
    int length = b3.length();
    if (length == 0) {
      LOG.fine("Invalid input: empty");
      return null;
    }

    int pos = 0;
    if (pos + 1 == length) { // possibly sampling flags
      return tryParseSamplingFlags(b3, pos);
    }

    // At this point we minimally expect a traceId-spanId pair
    if (length < 16 + 1 + 16 /* traceid64-spanid */) {
      LOG.fine("Invalid input: truncated");
      return null;
    } else if (length > FORMAT_MAX_LENGTH) {
      LOG.fine("Invalid input: too long");
      return null;
    }

    long traceIdHigh, traceId;
    if (b3.charAt(pos + 32) == '-') {
      traceIdHigh = tryParse16HexCharacters(b3, pos, length);
      pos += 16; // upper 64 bits of the trace ID
      traceId = tryParse16HexCharacters(b3, pos, length);
    } else {
      traceIdHigh = 0L;
      traceId = tryParse16HexCharacters(b3, pos, length);
    }
    pos += 16; // traceId
    if (!checkHyphen(b3, pos++)) return null;

    if (traceIdHigh == 0L && traceId == 0L) {
      LOG.fine("Invalid input: expected a 16 or 32 lower hex trace ID at offset 0");
      return null;
    }

    long spanId = tryParse16HexCharacters(b3, pos, length);
    if (spanId == 0L) {
      LOG.log(Level.FINE, "Invalid input: expected a 16 lower hex span ID at offset {0}", pos);
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
        LOG.fine("Invalid input: truncated");
        return null;
      }
      if (!checkHyphen(b3, pos++)) return null;

      // If our position is at the end of the string, or another delimiter is one character past our
      // position, try to read sampled status.
      if (length == pos + 1 || delimiterFollowsPos(b3, pos, length)) {
        flags = parseFlags(b3, pos);
        if (flags == 0) return null;
        pos++; // consume the sampled status
      }

      if (length > pos) {
        // If we are at this point, we should have a parent ID, encoded as "-[0-9a-f]{16}"
        if (length != pos + 17) {
          LOG.fine("Invalid input: truncated");
          return null;
        }

        if (!checkHyphen(b3, pos++)) return null;
        parentId = tryParse16HexCharacters(b3, pos, length);
        if (parentId == 0L) {
          LOG.log(Level.FINE,
              "Invalid input: expected a 16 lower hex parent ID at offset {0}", pos);
          return null;
        }
      }
    }

    boolean debug = (flags & FLAG_DEBUG) == FLAG_DEBUG;
    return TraceData.create(SpanId.builder()
        .traceIdHigh(traceIdHigh)
        .traceId(traceId)
        .parentId(parentId != 0L ? parentId : null)
        .spanId(spanId)
        .sampled(debug ? Boolean.TRUE : sampled(flags))
        .debug(debug)
        .build()
    );
  }

  static Boolean sampled(int flags) {
    return (flags & FLAG_SAMPLED_SET) == FLAG_SAMPLED_SET ?
        (flags & FLAG_SAMPLED) == FLAG_SAMPLED : null;
  }

  static TraceData tryParseSamplingFlags(String b3, int pos) {
    int flags = parseFlags(b3, pos);
    if (flags == 0) return null;
    if ((flags & FLAG_DEBUG) == FLAG_DEBUG // brave 3 doesn't support debug
        || (flags & FLAG_SAMPLED) == FLAG_SAMPLED) {
      return TraceData.SAMPLED;
    }
    return TraceData.NOT_SAMPLED;
  }

  static boolean checkHyphen(String b3, int pos) {
    if (b3.charAt(pos) == '-') return true;
    LOG.log(Level.FINE, "Invalid input: expected a hyphen(-) delimiter offset {0}", pos);
    return false;
  }

  static boolean delimiterFollowsPos(String b3, int pos, int end) {
    return (end >= pos + 2) && b3.charAt(pos + 1) == '-';
  }

  static long tryParse16HexCharacters(String lowerHex, int index, int end) {
    int endIndex = index + 16;
    if (endIndex > end) return 0L;
    return lenientConvertToLong(lowerHex, index, endIndex);
  }

  static int parseFlags(String b3, int pos) {
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
    LOG.log(Level.FINE, "Invalid input: expected 0, 1 or d for sampled at offset {0}", pos);
  }
}
