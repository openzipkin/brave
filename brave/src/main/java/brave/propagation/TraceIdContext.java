package brave.propagation;

import brave.internal.Nullable;
import com.google.auto.value.AutoValue;

import static brave.internal.HexCodec.writeHexLong;

/**
 * Contains inbound trace ID and sampling flags, used when users control the root trace ID, but not
 * the span ID (ex Amazon X-Ray or other correlation).
 */
@AutoValue
//@Immutable
public abstract class TraceIdContext extends SamplingFlags {

  public static Builder newBuilder() {
    return new AutoValue_TraceIdContext.Builder().traceIdHigh(0L).debug(false);
  }

  /** When non-zero, the trace containing this span uses 128-bit trace identifiers. */
  public abstract long traceIdHigh();

  /** Unique 8-byte identifier for a trace, set on all spans within it. */
  public abstract long traceId();

  // override as auto-value can't currently read the super-class's nullable annotation.
  @Override @Nullable public abstract Boolean sampled();

  public abstract Builder toBuilder();

  /** Returns {@code $traceId} */
  @Override
  public String toString() {
    boolean traceHi = traceIdHigh() != 0;
    char[] result = new char[traceHi ? 32 : 16];
    int pos = 0;
    if (traceHi) {
      writeHexLong(result, pos, traceIdHigh());
      pos += 16;
    }
    writeHexLong(result, pos, traceId());
    return new String(result);
  }

  @AutoValue.Builder
  public static abstract class Builder {
    /** @see TraceIdContext#traceIdHigh() */
    public abstract Builder traceIdHigh(long traceIdHigh);

    /** @see TraceIdContext#traceId() */
    public abstract Builder traceId(long traceId);

    /** @see TraceIdContext#sampled */
    public abstract Builder sampled(@Nullable Boolean nullableSampled);

    /** @see TraceIdContext#debug() */
    public abstract Builder debug(boolean debug);

    public abstract TraceIdContext build();

    Builder() { // no external implementations
    }
  }

  TraceIdContext() { // no external implementations
  }
}
