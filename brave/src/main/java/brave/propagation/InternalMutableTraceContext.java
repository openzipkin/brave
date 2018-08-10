package brave.propagation;

import brave.internal.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static brave.internal.HexCodec.lenientLowerHexToUnsignedLong;
import static brave.propagation.SamplingFlags.FLAG_DEBUG;
import static brave.propagation.SamplingFlags.FLAG_SAMPLED;
import static brave.propagation.SamplingFlags.FLAG_SAMPLED_SET;
import static brave.propagation.TraceContext.FLAG_SHARED;

abstract class InternalMutableTraceContext {
  abstract Logger logger();

  long traceIdHigh, traceId, parentId, spanId;
  int flags = 0; // bit field for sampled and debug
  List<Object> extra = Collections.emptyList();

  /**
   * Returns true when {@link TraceContext#traceId()} and potentially also {@link
   * TraceContext#traceIdHigh()} were parsed from the input. This assumes the input is valid, an up
   * to 32 character lower-hex string.
   *
   * <p>Returns boolean, not this, for conditional, exception free parsing:
   *
   * <p>Example use:
   * <pre>{@code
   * // Attempt to parse the trace ID or break out if unsuccessful for any reason
   * String traceIdString = getter.get(carrier, key);
   * if (!builder.parseTraceId(traceIdString, propagation.traceIdKey)) {
   *   return TraceContextOrSamplingFlags.EMPTY;
   * }
   * }</pre>
   *
   * @param traceIdString the 1-32 character lowerhex string
   * @param key the name of the propagation field representing the trace ID; only using in logging
   * @return false if the input is null or malformed
   */
  // temporarily package protected until we figure out if this is reusable enough to expose
  final boolean parseTraceId(String traceIdString, Object key) {
    if (isNull(key, traceIdString)) return false;
    int length = traceIdString.length();
    if (invalidIdLength(key, length, 32)) return false;

    // left-most characters, if any, are the high bits
    int traceIdIndex = Math.max(0, length - 16);
    if (traceIdIndex > 0) {
      traceIdHigh = lenientLowerHexToUnsignedLong(traceIdString, 0, traceIdIndex);
      if (traceIdHigh == 0) {
        maybeLogNotLowerHex(key, traceIdString);
        return false;
      }
    }

    // right-most up to 16 characters are the low bits
    traceId = lenientLowerHexToUnsignedLong(traceIdString, traceIdIndex, length);
    if (traceId == 0) {
      maybeLogNotLowerHex(key, traceIdString);
      return false;
    }
    return true;
  }

  /** Parses the parent id from the input string. Returns true if the ID was missing or valid. */
  final <C, K> boolean parseParentId(Propagation.Getter<C, K> getter, C carrier, K key) {
    String parentIdString = getter.get(carrier, key);
    if (parentIdString == null) return true; // absent parent is ok
    int length = parentIdString.length();
    if (invalidIdLength(key, length, 16)) return false;

    parentId = lenientLowerHexToUnsignedLong(parentIdString, 0, length);
    if (parentId != 0) return true;
    maybeLogNotLowerHex(key, parentIdString);
    return false;
  }

  /** Parses the span id from the input string. Returns true if the ID is valid. */
  final <C, K> boolean parseSpanId(Propagation.Getter<C, K> getter, C carrier, K key) {
    String spanIdString = getter.get(carrier, key);
    if (isNull(key, spanIdString)) return false;
    int length = spanIdString.length();
    if (invalidIdLength(key, length, 16)) return false;

    spanId = lenientLowerHexToUnsignedLong(spanIdString, 0, length);
    if (spanId == 0) {
      maybeLogNotLowerHex(key, spanIdString);
      return false;
    }
    return true;
  }

  void _shared(boolean shared) {
    if (shared) {
      flags |= FLAG_SHARED;
    } else {
      flags &= ~FLAG_SHARED;
    }
  }

  /**
   * Adds iff there is not already an instance of the given type.
   *
   * @see TraceContext#extra()
   */
  final void _addExtra(Object extra) {
    if (extra == null) throw new NullPointerException("extra == null");
    if (findExtra(this.extra, extra.getClass()) != null) return;
    if (!(this.extra instanceof ArrayList)) {
      this.extra = new ArrayList<>(this.extra); // make it mutable
    }
    this.extra.add(extra);
  }

  void _sampled(boolean sampled) {
    flags |= FLAG_SAMPLED_SET;
    if (sampled) {
      flags |= FLAG_SAMPLED;
    } else {
      flags &= ~FLAG_SAMPLED;
    }
  }

  void _sampled(@Nullable Boolean sampled) {
    if (sampled != null) {
      _sampled((boolean) sampled);
    } else {
      flags &= ~FLAG_SAMPLED_SET;
    }
  }

  /** Ensures sampled is set when debug is */
  void _debug(boolean debug) {
    if (debug) {
      flags |= FLAG_DEBUG;
      flags |= FLAG_SAMPLED_SET;
      flags |= FLAG_SAMPLED;
    } else {
      flags &= ~FLAG_DEBUG;
    }
  }

  /** Returns an instance of the given type in extra if present or null if not. */
  static <T> T findExtra(List<Object> extra, Class<T> type) {
    if (type == null) throw new NullPointerException("type == null");
    for (int i = 0, length = extra.size(); i < length; i++) {
      Object nextExtra = extra.get(i);
      if (extra.get(i).getClass() == type) return (T) nextExtra;
    }
    return null;
  }

  static List<Object> ensureImmutable(List<Object> extra) {
    if (isImmutable(extra)) return extra;
    // Faster to make a copy than check the type to see if it is already a singleton list
    if (extra.size() == 1) return Collections.singletonList(extra.get(0));
    return Collections.unmodifiableList(new ArrayList<>(extra));
  }

  private static boolean isImmutable(List<Object> extra) {
    if (extra == Collections.EMPTY_LIST) return true;
    // avoid copying datastructure by trusting certain names.
    String simpleName = extra.getClass().getSimpleName();
    if (simpleName.equals("SingletonList")
        || simpleName.startsWith("Unmodifiable")
        || simpleName.contains("Immutable")) {
      return true;
    }
    return false;
  }

  private boolean invalidIdLength(Object key, int length, int max) {
    if (length > 1 && length <= max) return false;
    Logger log = logger();
    if (log.isLoggable(Level.FINE)) {
      log.fine(key + " should be a 1 to " + max + " character lower-hex string with no prefix");
    }
    return true;
  }

  private boolean isNull(Object key, String maybeNull) {
    if (maybeNull != null) return false;
    Logger log = logger();
    if (log.isLoggable(Level.FINE)) log.fine(key + " was null");
    return true;
  }

  private void maybeLogNotLowerHex(Object key, String notLowerHex) {
    Logger log = logger();
    if (log.isLoggable(Level.FINE)) {
      log.fine(key + ": " + notLowerHex + " is not a lower-hex string");
    }
  }
}