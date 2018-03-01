package brave.propagation;

import brave.internal.Nullable;
import java.util.logging.Level;
import java.util.logging.Logger;

import static brave.internal.HexCodec.lenientLowerHexToUnsignedLong;
import static brave.internal.HexCodec.writeHexLong;

/**
 * Contains inbound trace ID and sampling flags, used when users control the root trace ID, but not
 * the span ID (ex Amazon X-Ray or other correlation).
 */
//@Immutable
public final class TraceIdContext extends SamplingFlags {
  static final Logger LOG = Logger.getLogger(TraceIdContext.class.getName());

  public static Builder newBuilder() {
    return new Builder();
  }

  /** When non-zero, the trace containing this span uses 128-bit trace identifiers. */
  public long traceIdHigh() {
    return traceIdHigh;
  }

  /** Unique 8-byte identifier for a trace, set on all spans within it. */
  public long traceId() {
    return traceId;
  }

  /** {@inheritDoc} */
  @Override @Nullable public Boolean sampled() {
    return sampled(flags);
  }

  /** {@inheritDoc} */
  @Override public boolean debug() {
    return debug(flags);
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  /** Returns {@code $traceId} */
  @Override
  public String toString() {
    boolean traceHi = traceIdHigh != 0;
    char[] result = new char[traceHi ? 32 : 16];
    int pos = 0;
    if (traceHi) {
      writeHexLong(result, pos, traceIdHigh);
      pos += 16;
    }
    writeHexLong(result, pos, traceId);
    return new String(result);
  }

  public static final class Builder extends InternalBuilder {
    Builder(TraceIdContext context) { // no external implementations
      traceIdHigh = context.traceIdHigh;
      traceId = context.traceId;
      flags = context.flags;
    }

    /** @see TraceIdContext#traceIdHigh() */
    public Builder traceIdHigh(long traceIdHigh) {
      this.traceIdHigh = traceIdHigh;
      return this;
    }

    /** @see TraceIdContext#traceId() */
    public Builder traceId(long traceId) {
      this.traceId = traceId;
      return this;
    }

    /** @see TraceIdContext#sampled() */
    @Override public Builder sampled(boolean sampled) {
      super.sampled(sampled);
      return this;
    }

    /** @see TraceIdContext#sampled() */
    @Override public Builder sampled(@Nullable Boolean sampled) {
      super.sampled(sampled);
      return this;
    }

    /** @see TraceIdContext#debug() */
    @Override public Builder debug(boolean debug) {
      super.debug(debug);
      return this;
    }

    public final TraceIdContext build() {
      if (traceId == 0L) throw new IllegalStateException("Missing: traceId");
      return new TraceIdContext(this);
    }

    @Override Logger logger() {
      return LOG;
    }

    Builder() { // no external implementations
    }
  }

  final long traceIdHigh, traceId;
  final int flags; // bit field for sampled and debug

  TraceIdContext(Builder builder) { // no external implementations
    traceIdHigh = builder.traceIdHigh;
    traceId = builder.traceId;
    flags = builder.flags;
  }

  /** Only includes mandatory fields {@link #traceIdHigh()} and {@link #traceId()} */
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof TraceIdContext)) return false;
    TraceIdContext that = (TraceIdContext) o;
    return (traceIdHigh == that.traceIdHigh) && (traceId == that.traceId);
  }

  /** Only includes mandatory fields {@link #traceIdHigh()} and {@link #traceId()} */
  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) ((traceIdHigh >>> 32) ^ traceIdHigh);
    h *= 1000003;
    h ^= (int) ((traceId >>> 32) ^ traceId);
    return h;
  }

  static abstract class InternalBuilder {
    abstract Logger logger();

    long traceIdHigh, traceId;
    int flags = 0; // bit field for sampled and debug

    /**
     * Returns true when {@link TraceContext#traceId()} and potentially also {@link TraceContext#traceIdHigh()}
     * were parsed from the input. This assumes the input is valid, an up to 32 character lower-hex
     * string.
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

    boolean invalidIdLength(Object key, int length, int max) {
      if (length > 1 && length <= max) return false;
      Logger log = logger();
      if (log.isLoggable(Level.FINE)) {
        log.fine(key + " should be a 1 to " + max + " character lower-hex string with no prefix");
      }
      return true;
    }

    boolean isNull(Object key, String maybeNull) {
      if (maybeNull != null) return false;
      Logger log = logger();
      if (log.isLoggable(Level.FINE)) log.fine(key + " was null");
      return true;
    }

    void maybeLogNotLowerHex(Object key, String notLowerHex) {
      Logger log = logger();
      if (log.isLoggable(Level.FINE)) {
        log.fine(key + ": " + notLowerHex + " is not a lower-hex string");
      }
    }

    InternalBuilder sampled(boolean sampled) {
      flags |= FLAG_SAMPLED_SET;
      if (sampled) {
        flags |= FLAG_SAMPLED;
      } else {
        flags &= ~FLAG_SAMPLED;
      }
      return this;
    }

    InternalBuilder sampled(@Nullable Boolean sampled) {
      if (sampled != null) return sampled((boolean) sampled);
      flags &= ~FLAG_SAMPLED_SET;
      return this;
    }

    InternalBuilder debug(boolean debug) {
      if (debug) {
        flags |= FLAG_DEBUG;
      } else {
        flags &= ~FLAG_DEBUG;
      }
      return this;
    }
  }
}
