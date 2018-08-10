package brave.propagation;

import brave.internal.Nullable;
import java.util.logging.Logger;

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

  public static final class Builder extends InternalMutableTraceContext {
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
    public Builder sampled(boolean sampled) {
      super._sampled(sampled);
      return this;
    }

    /** @see TraceIdContext#sampled() */
    public Builder sampled(@Nullable Boolean sampled) {
      super._sampled(sampled);
      return this;
    }

    /** @see TraceIdContext#debug() */
    public Builder debug(boolean debug) {
      super._debug(debug);
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

  TraceIdContext(Builder builder) { // no external implementations
    super(builder.flags);
    traceIdHigh = builder.traceIdHigh;
    traceId = builder.traceId;
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
}
