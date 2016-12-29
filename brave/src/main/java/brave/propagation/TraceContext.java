package brave.propagation;

import brave.internal.Nullable;
import com.google.auto.value.AutoValue;

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
@AutoValue
public abstract class TraceContext extends SamplingFlags {

  /**
   * Used to send the trace context downstream. For example, as http headers.
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
   * @see brave.Tracer#joinSpan
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
    return new AutoValue_TraceContext.Builder().traceIdHigh(0L).debug(false).shared(false);
  }

  /** When non-zero, the trace containing this span uses 128-bit trace identifiers. */
  public abstract long traceIdHigh();

  /** Unique 8-byte identifier for a trace, set on all spans within it. */
  public abstract long traceId();

  /** The parent's {@link #spanId} or null if this the root span in a trace. */
  @Nullable public abstract Long parentId();

  // override as auto-value can't currently read the super-class's nullable annotation.
  @Override @Nullable public abstract Boolean sampled();

  /**
   * Unique 8-byte identifier of this span within a trace.
   *
   * <p>A span is uniquely identified in storage by ({@linkplain #traceId}, {@linkplain #spanId}).
   */
  public abstract long spanId();

  /**
   * True if we are contributing to a span started by another tracer (ex on a different host).
   * Defaults to false.
   *
   * <p>When an RPC trace is client-originated, it will be sampled and the same span ID is used for
   * the server side. However, the server shouldn't set span.timestamp or duration since it didn't
   * start the span.
   */
  public abstract boolean shared();

  public abstract Builder toBuilder();

  /** Returns the hex representation of the span's trace ID */
  public String traceIdString() {
    if (traceIdHigh() != 0) {
      char[] result = new char[32];
      writeHexLong(result, 0, traceIdHigh());
      writeHexLong(result, 16, traceId());
      return new String(result);
    }
    char[] result = new char[16];
    writeHexLong(result, 0, traceId());
    return new String(result);
  }

  /** Returns {@code $traceId/$spanId} */
  @Override
  public String toString() {
    boolean traceHi = traceIdHigh() != 0;
    char[] result = new char[((traceHi ? 3 : 2) * 16) + 1]; // 2 ids and the delimiter
    int pos = 0;
    if (traceHi) {
      writeHexLong(result, pos, traceIdHigh());
      pos += 16;
    }
    writeHexLong(result, pos, traceId());
    pos += 16;
    result[pos++] = '/';
    writeHexLong(result, pos, spanId());
    return new String(result);
  }

  @AutoValue.Builder
  public static abstract class Builder {
    /** @see TraceContext#traceIdHigh() */
    public abstract Builder traceIdHigh(long traceIdHigh);

    /** @see TraceContext#traceId() */
    public abstract Builder traceId(long traceId);

    /** @see TraceContext#parentId */
    public abstract Builder parentId(@Nullable Long parentId);

    /** @see TraceContext#spanId */
    public abstract Builder spanId(long spanId);

    /** @see TraceContext#sampled */
    public abstract Builder sampled(Boolean nullableSampled);

    /** @see TraceContext#debug() */
    public abstract Builder debug(boolean debug);

    /** @see TraceContext#shared() */
    public abstract Builder shared(boolean shared);

    public abstract TraceContext build();

    abstract Boolean sampled();

    abstract boolean debug();

    Builder() { // no external implementations
    }
  }

  TraceContext() { // no external implementations
  }
}
