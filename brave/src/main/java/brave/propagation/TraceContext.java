package brave.propagation;

import brave.Tracer;
import brave.internal.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static brave.internal.HexCodec.lenientLowerHexToUnsignedLong;
import static brave.internal.HexCodec.writeHexLong;

/**
 * Contains trace identifiers and sampling data propagated in and out-of-process.
 *
 * <p>Particularly, this includes trace identifiers and sampled state.
 *
 * <p>The implementation was originally {@code com.github.kristofa.brave.SpanId}, which was a
 * port of {@code com.twitter.finagle.tracing.TraceId}. Unlike these mentioned, this type does not
 * expose a single binary representation. That's because propagation forms can now vary.
 */
//@Immutable
public final class TraceContext extends SamplingFlags {
  static final Logger LOG = Logger.getLogger(Tracer.class.getName());

  /**
   * Used to send the trace context downstream. For example, as http headers.
   *
   * <p>For example, to put the context on an {@link java.net.HttpURLConnection}, you can do this:
   * <pre>{@code
   * // in your constructor
   * injector = tracing.propagation().injector(URLConnection::setRequestProperty);
   *
   * // later in your code, reuse the function you created above to add trace headers
   * HttpURLConnection connection = (HttpURLConnection) new URL("http://myserver").openConnection();
   * injector.inject(span.context(), connection);
   * }</pre>
   */
  public interface Injector<C> {
    /**
     * Usually calls a setter for each propagation field to send downstream.
     *
     * @param traceContext possibly unsampled.
     * @param carrier holds propagation fields. For example, an outgoing message or http request.
     */
    void inject(TraceContext traceContext, C carrier);
  }

  /**
   * Used to continue an incoming trace. For example, by reading http headers.
   *
   * @see brave.Tracer#nextSpan(TraceContextOrSamplingFlags)
   */
  public interface Extractor<C> {

    /**
     * Returns either a trace context or sampling flags parsed from the carrier. If nothing was
     * parsable, sampling flags will be set to {@link SamplingFlags#EMPTY}.
     *
     * @param carrier holds propagation fields. For example, an incoming message or http request.
     */
    TraceContextOrSamplingFlags extract(C carrier);
  }

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

  /**
   * The parent's {@link #spanId} or null if this the root span in a trace.
   *
   * @see #parentIdAsLong()
   */
  @Nullable public final Long parentId() {
    return parentId != 0 ? parentId : null;
  }

  /**
   * Like {@link #parentId()} except returns a primitive where zero implies absent.
   *
   * <p>Using this method will avoid allocation, so is encouraged when copying data.
   */
  public long parentIdAsLong() {
    return parentId;
  }

  /** {@inheritDoc} */
  @Override @Nullable public Boolean sampled() {
    return sampled(flags);
  }

  /** {@inheritDoc} */
  @Override public boolean debug() {
    return debug(flags);
  }

  /**
   * Unique 8-byte identifier of this span within a trace.
   *
   * <p>A span is uniquely identified in storage by ({@linkplain #traceId}, {@linkplain #spanId}).
   */
  public long spanId() {
    return spanId;
  }

  /** @deprecated it is unnecessary overhead to propagate this property */
  @Deprecated public final boolean shared() {
    return false; // shared is set internally on Tracer.join
  }

  /**
   * Returns a list of additional data propagated through this trace.
   *
   * <p>The contents are intentionally opaque, deferring to {@linkplain Propagation} to define. An
   * example implementation could be storing a class containing a correlation value, which is
   * extracted from incoming requests and injected as-is onto outgoing requests.
   *
   * <p>Implementations are responsible for scoping any data stored here. This can be performed
   * when {@link Propagation.Factory#decorate(TraceContext)} is called.
   */
  public List<Object> extra() {
    return extra;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  /** Returns the hex representation of the span's trace ID */
  public String traceIdString() {
    if (traceIdHigh != 0) {
      char[] result = new char[32];
      writeHexLong(result, 0, traceIdHigh);
      writeHexLong(result, 16, traceId);
      return new String(result);
    }
    char[] result = new char[16];
    writeHexLong(result, 0, traceId);
    return new String(result);
  }

  /** Returns {@code $traceId/$spanId} */
  @Override
  public String toString() {
    boolean traceHi = traceIdHigh != 0;
    char[] result = new char[((traceHi ? 3 : 2) * 16) + 1]; // 2 ids and the delimiter
    int pos = 0;
    if (traceHi) {
      writeHexLong(result, pos, traceIdHigh);
      pos += 16;
    }
    writeHexLong(result, pos, traceId);
    pos += 16;
    result[pos++] = '/';
    writeHexLong(result, pos, spanId);
    return new String(result);
  }

  public static final class Builder {
    long traceIdHigh, traceId, parentId, spanId;
    int flags = 0; // bit field for sampled and debug
    List<Object> extra = Collections.emptyList();

    Builder(TraceContext context) { // no external implementations
      traceIdHigh = context.traceIdHigh;
      traceId = context.traceId;
      parentId = context.parentId;
      spanId = context.spanId;
      flags = context.flags;
      extra = context.extra;
    }

    /** @see TraceContext#traceIdHigh() */
    public Builder traceIdHigh(long traceIdHigh) {
      this.traceIdHigh = traceIdHigh;
      return this;
    }

    /** @see TraceContext#traceId() */
    public Builder traceId(long traceId) {
      this.traceId = traceId;
      return this;
    }

    /** @see TraceContext#parentIdAsLong() */
    public Builder parentId(long parentId) {
      this.parentId = parentId;
      return this;
    }

    /** @see TraceContext#parentId() */
    public Builder parentId(@Nullable Long parentId) {
      if (parentId == null) parentId = 0L;
      this.parentId = parentId;
      return this;
    }

    /** @see TraceContext#spanId() */
    public Builder spanId(long spanId) {
      this.spanId = spanId;
      return this;
    }

    /** @see TraceContext#sampled() */
    public Builder sampled(boolean sampled) {
      flags |= FLAG_SAMPLED_SET;
      if (sampled) {
        flags |= FLAG_SAMPLED;
      } else {
        flags &= ~FLAG_SAMPLED;
      }
      return this;
    }

    /** @see TraceContext#sampled() */
    public Builder sampled(@Nullable Boolean sampled) {
      if (sampled != null) return sampled((boolean) sampled);
      flags &= ~FLAG_SAMPLED_SET;
      return this;
    }

    /** @see TraceContext#debug() */
    public Builder debug(boolean debug) {
      if (debug) {
        flags |= (FLAG_DEBUG | FLAG_SAMPLED_SET | FLAG_SAMPLED);
      } else {
        flags &= ~FLAG_DEBUG;
      }
      return this;
    }

    /** @deprecated it is unnecessary overhead to propagate this property */
    @Deprecated public Builder shared(boolean shared) {
      // this is not a propagated property, rather set internal to Tracer.join
      return this;
    }

    /** @see TraceContext#extra() */
    public Builder extra(List<Object> extra) {
      this.extra = ensureImmutable(extra);
      return this;
    }

    /**
     * Returns true when {@link TraceContext#traceId()} and potentially also {@link
     * TraceContext#traceIdHigh()} were parsed from the input. This assumes the input is valid, an
     * up to 32 character lower-hex string.
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
     * @param key the name of the propagation field representing the trace ID; only using in
     * logging
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
          maybeLogNotLowerHex(traceIdString);
          return false;
        }
      }

      // right-most up to 16 characters are the low bits
      traceId = lenientLowerHexToUnsignedLong(traceIdString, traceIdIndex, length);
      if (traceId == 0) {
        maybeLogNotLowerHex(traceIdString);
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
      maybeLogNotLowerHex(parentIdString);
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
        maybeLogNotLowerHex(spanIdString);
        return false;
      }
      return true;
    }

    boolean invalidIdLength(Object key, int length, int max) {
      if (length > 1 && length <= max) return false;

      assert max == 32 || max == 16;
      LOG.log(Level.FINE, max == 32
          ? "{0} should be a 1 to 32 character lower-hex string with no prefix"
          : "{0} should be a 1 to 16 character lower-hex string with no prefix", key);

      return true;
    }

    boolean isNull(Object key, String maybeNull) {
      if (maybeNull != null) return false;
      LOG.log(Level.FINE, "{0} was null", key);
      return true;
    }

    void maybeLogNotLowerHex(String notLowerHex) {
      LOG.log(Level.FINE, "{0} is not a lower-hex string", notLowerHex);
    }

    public final TraceContext build() {
      String missing = "";
      if (traceId == 0L) missing += " traceId";
      if (spanId == 0L) missing += " spanId";
      if (!"".equals(missing)) throw new IllegalStateException("Missing: " + missing);
      return new TraceContext(this);
    }

    Builder() { // no external implementations
    }
  }

  final long traceIdHigh, traceId, parentId, spanId;
  final int flags; // bit field for sampled and debug
  final List<Object> extra;

  TraceContext(Builder builder) { // no external implementations
    traceIdHigh = builder.traceIdHigh;
    traceId = builder.traceId;
    parentId = builder.parentId;
    spanId = builder.spanId;
    flags = builder.flags;
    extra = builder.extra;
  }

  /** Only includes mandatory fields {@link #traceIdHigh()}, {@link #traceId()}, {@link #spanId()} */
  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof TraceContext)) return false;
    TraceContext that = (TraceContext) o;
    return (traceIdHigh == that.traceIdHigh)
        && (traceId == that.traceId)
        && (spanId == that.spanId);
  }

  /** Only includes mandatory fields {@link #traceIdHigh()}, {@link #traceId()}, {@link #spanId()} */
  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) ((traceIdHigh >>> 32) ^ traceIdHigh);
    h *= 1000003;
    h ^= (int) ((traceId >>> 32) ^ traceId);
    h *= 1000003;
    h ^= (int) ((spanId >>> 32) ^ spanId);
    return h;
  }

  static List<Object> ensureImmutable(List<Object> extra) {
    if (extra == Collections.EMPTY_LIST) return extra;
    // Faster to make a copy than check the type to see if it is already a singleton list
    if (extra.size() == 1) return Collections.singletonList(extra.get(0));
    return Collections.unmodifiableList(new ArrayList<>(extra));
  }
}
