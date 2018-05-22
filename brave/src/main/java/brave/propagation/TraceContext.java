package brave.propagation;

import brave.internal.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
  static final Logger LOG = Logger.getLogger(TraceContext.class.getName());

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
   * Used to join an incoming trace. For example, by reading http headers.
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

  /**
   * Returns a list of additional data propagated through this trace.
   *
   * <p>The contents are intentionally opaque, deferring to {@linkplain Propagation} to define. An
   * example implementation could be storing a class containing a correlation value, which is
   * extracted from incoming requests and injected as-is onto outgoing requests.
   *
   * <p>Implementations are responsible for scoping any data stored here. This can be performed when
   * {@link Propagation.Factory#decorate(TraceContext)} is called.
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

  public static final class Builder extends InternalBuilder {
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
    @Override public Builder sampled(boolean sampled) {
      super.sampled(sampled);
      return this;
    }

    /** @see TraceContext#sampled() */
    @Override public Builder sampled(@Nullable Boolean sampled) {
      super.sampled(sampled);
      return this;
    }

    /** @see TraceContext#debug() */
    @Override public Builder debug(boolean debug) {
      super.debug(debug);
      return this;
    }

    /** @see TraceContext#extra() */
    public Builder extra(List<Object> extra) {
      this.extra = ensureImmutable(extra);
      return this;
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

  // parseXXX methods package protected until we figure out if this is reusable enough to expose
  static class InternalBuilder extends TraceIdContext.InternalBuilder {
    long parentId, spanId;

    /** Parses the parent id from the input string. Returns true if the ID was missing or valid. */
    final <C, K> boolean parseParentId(Propagation.Getter<C, K> getter, C carrier, K key) {
      String parentIdString = getter.get(carrier, key);
      if (parentIdString == null) return true; // absent parent is ok
      int length = parentIdString.length();
      if (invalidIdLength(key, length, 16)) return false;

      parentId = lenientLowerHexToUnsignedLong(parentIdString, 0, length);
      if (parentId == 0) {
        maybeLogNotLowerHex(key, parentIdString);
        return false;
      }
      return true;
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

    @Override Logger logger() {
      return LOG;
    }
  }

  static List<Object> ensureImmutable(List<Object> extra) {
    if (extra == Collections.EMPTY_LIST) return extra;
    // Faster to make a copy than check the type to see if it is already a singleton list
    if (extra.size() == 1) return Collections.singletonList(extra.get(0));
    return Collections.unmodifiableList(new ArrayList<>(extra));
  }
}
