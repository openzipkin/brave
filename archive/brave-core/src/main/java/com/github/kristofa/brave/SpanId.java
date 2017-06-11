package com.github.kristofa.brave;

import com.github.kristofa.brave.internal.Nullable;
import java.nio.ByteBuffer;

import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * Contains trace data that's propagated in-band across requests, sometimes known as Baggage.
 *
 * <p>This implementation is biased towards a fixed-length binary serialization format that doesn't
 * have a way to represent an absent parent (root span). In this serialized form, a root span is
 * when all three ids are the same. Alternatively, you can use {@link #nullableParentId}.
 *
 * <p>Particularly, this includes sampled state, and a portable binary representation. The
 * implementation is a port of {@code com.twitter.finagle.tracing.TraceId}.
 * @deprecated Replaced by {@code brave.propagation.TraceContext}
 */
@Deprecated
public final class SpanId {

  public static final int FLAG_DEBUG = 1 << 0;
  /** When set, we can interpret {@link #FLAG_SAMPLED} as a set value. */
  public static final int FLAG_SAMPLING_SET = 1 << 1;
  public static final int FLAG_SAMPLED = 1 << 2;
  /**
   * When set, we can ignore the value of the {@link #parentId}
   *
   * <p>While many zipkin systems re-use a trace id as the root span id, we know that some don't.
   * With this flag, we can tell for sure if the span is root as opposed to the convention trace id
   * == span id == parent id.
   */
  public static final int FLAG_IS_ROOT = 1 << 3;

  /**
   * Creates a new span id.
   *
   * @param traceId Trace Id.
   * @param spanId Span Id.
   * @param parentSpanId Nullable parent span id.
   * @deprecated Please use {@link SpanId.Builder}
   */
  @Deprecated
  public static SpanId create(long traceId, long spanId, @Nullable Long parentSpanId) {
    return SpanId.builder().traceId(traceId).parentId(parentSpanId).spanId(spanId).build();
  }

  /**
   * @deprecated Please use {@link SpanId.Builder}
   */
  @Deprecated
  public SpanId(long traceId, long parentId, long spanId, long flags) {
    this(new Builder().traceId(parentId == traceId ? parentId : traceId)
        .parentId(parentId == spanId ? null : parentId)
        .spanId(spanId)
        .flags(flags));
  }

  SpanId(Builder builder) {
    checkNotNull(builder.spanId, "spanId");
    this.traceIdHigh = builder.traceIdHigh;
    this.traceId = builder.traceId != null ? builder.traceId : builder.spanId;
    this.parentId = builder.nullableParentId != null ? builder.nullableParentId : this.traceId;
    this.spanId = builder.spanId;
    this.flags = builder.flags;
    this.shared = builder.shared;
  }

  /** Deserializes this from a big-endian byte array */
  public static SpanId fromBytes(byte[] bytes) {
    checkNotNull(bytes, "bytes");
    if (bytes.length != 32 && bytes.length != 40) {
      throw new IllegalArgumentException("bytes.length " + bytes.length + " != 32 or 40");
    }

    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    Builder builder = new Builder();
    builder.spanId(buffer.getLong(0));
    builder.parentId(buffer.getLong(8));
    if (bytes.length == 32) {
      builder.traceId(buffer.getLong(16));
      builder.flags(buffer.getLong(24));
    } else {
      builder.traceIdHigh(buffer.getLong(16));
      builder.traceId(buffer.getLong(24));
      builder.flags(buffer.getLong(32));
    }
    return new SpanId(builder);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Get Trace id.
   *
   * @return Trace id.
   * @deprecated use {@link #traceId}
   */
  @Deprecated
  public long getTraceId() {
    return traceId;
  }

  /**
   * Get span id.
   *
   * @return span id.
   * @deprecated use {@link #spanId}
   */
  @Deprecated
  public long getSpanId() {
    return spanId;
  }

  /**
   * Get parent span id.
   *
   * @return Parent span id. Can be <code>null</code>.
   * @deprecated use {@link #nullableParentId()}
   */
  @Deprecated
  @Nullable
  public Long getParentSpanId() {
    return nullableParentId();
  }

  /**
   * When non-zero, the trace containing this span uses 128-bit trace identifiers.
   *
   * @since 3.15
   */
  public final long traceIdHigh;

  /**
   * Unique 8-byte identifier for a trace, set on all spans within it.
   */
  public final long traceId;

  /**
   * The parent's {@link #spanId} or {@link #spanId} if this the root span in a trace.
   */
  public final long parentId;

  /** Returns null when this is a root span. */
  @Nullable
  public Long nullableParentId() {
    return root() ? null : parentId;
  }

  /**
   * Unique 8-byte identifier of this span within a trace.
   *
   * <p>A span is uniquely identified in storage by ({@linkplain #traceId}, {@code #id}).
   */
  public final long spanId;

  /** Returns true if this is the root span. */
  public final boolean root() {
    return (flags & FLAG_IS_ROOT) == FLAG_IS_ROOT || (parentId == traceId && parentId == spanId);
  }

  /**
   * True is a request to store this span even if it overrides sampling policy. Implies {@link
   * #sampled()}.
   */
  public final boolean debug() {
    return (flags & FLAG_DEBUG) == FLAG_DEBUG;
  }

  /**
   * Should we sample this request or not? True means sample, false means don't, null means we defer
   * decision to someone further down in the stack.
   */
  @Nullable
  public final Boolean sampled() {
    if (debug()) return true;
    return (flags & FLAG_SAMPLING_SET) == FLAG_SAMPLING_SET
        ? (flags & FLAG_SAMPLED) == FLAG_SAMPLED
        : null;
  }

  /** Raw flags encoded in {@link #bytes()} */
  public final long flags;

  /**
   * True if we are contributing to a span started by another tracer (ex on a different host).
   * Defaults to false.
   *
   * <p>When an RPC trace is client-originated, it will be sampled and the same span ID is used for
   * the server side. However, the server shouldn't set span.timestamp or duration since it didn't
   * start the span.
   */
  public final boolean shared;

  /** Serializes this into a big-endian byte array */
  public byte[] bytes() {
    boolean traceHi = traceIdHigh != 0;
    byte[] result = new byte[traceHi ? 40 : 32];
    ByteBuffer buffer = ByteBuffer.wrap(result);
    buffer.putLong(0, spanId);
    buffer.putLong(8, parentId);
    if (traceHi) {
      buffer.putLong(16, traceIdHigh);
      buffer.putLong(24, traceId);
      buffer.putLong(32, flags);
    } else {
      buffer.putLong(16, traceId);
      buffer.putLong(24, flags);
    }
    return result;
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  /** Returns {@code $traceId.$spanId<:$parentId} */
  @Override
  public String toString() {
    boolean traceHi = traceIdHigh != 0;
    char[] result = new char[((traceHi ? 4 : 3) * 16) + 3]; // 3 ids and the constant delimiters
    int pos = 0;
    if (traceHi) {
      writeHexLong(result, pos, traceIdHigh);
      pos += 16;
    }
    writeHexLong(result, pos, traceId);
    pos += 16;
    result[pos++] = '.';
    writeHexLong(result, pos, spanId);
    pos += 16;
    result[pos++] = '<';
    result[pos++] = ':';
    writeHexLong(result, pos, parentId);
    return new String(result);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SpanId) {
      SpanId that = (SpanId) o;
      return (this.traceIdHigh == that.traceIdHigh)
          && (this.traceId == that.traceId)
          && (this.spanId == that.spanId);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (traceIdHigh >>> 32) ^ traceIdHigh;
    h *= 1000003;
    h ^= (traceId >>> 32) ^ traceId;
    h *= 1000003;
    h ^= (spanId >>> 32) ^ spanId;
    return h;
  }

  /**
   * Returns the hex representation of the span's trace ID
   *
   * @since 3.15
   */
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

  public static final class Builder {
    long traceIdHigh = 0;
    Long traceId;
    Long nullableParentId;
    Long spanId;
    long flags;
    boolean shared;

    Builder(SpanId source) {
      this.traceIdHigh = source.traceIdHigh;
      this.traceId = source.traceId;
      this.nullableParentId = source.nullableParentId();
      this.spanId = source.spanId;
      this.flags = source.flags;
      this.shared = source.shared;
    }

    /** @see SpanId#traceIdHigh */
    public Builder traceIdHigh(long traceIdHigh) {
      this.traceIdHigh = traceIdHigh;
      return this;
    }

    /** @see SpanId#traceId */
    public Builder traceId(long traceId) {
      this.traceId = traceId;
      return this;
    }

    /**
     * If your trace ids are not span ids, you must call this method to indicate absent parent.
     *
     * @see SpanId#nullableParentId()
     */
    public Builder parentId(@Nullable Long parentId) {
      if (parentId == null) {
        this.flags |= FLAG_IS_ROOT;
      } else {
        this.flags &= ~FLAG_IS_ROOT;
      }
      this.nullableParentId = parentId;
      return this;
    }

    /** @see SpanId#spanId */
    public Builder spanId(long spanId) {
      this.spanId = spanId;
      return this;
    }

    /** @see SpanId#flags */
    public Builder flags(long flags) {
      this.flags = flags;
      return this;
    }

    /** @see SpanId#debug() */
    public Builder debug(boolean debug) {
      if (debug) {
        this.flags |= FLAG_DEBUG;
      } else {
        this.flags &= ~FLAG_DEBUG;
      }
      return this;
    }

    /** @see SpanId#shared */
    public Builder shared(boolean shared) {
      this.shared = shared;
      return this;
    }

    /** @see SpanId#sampled */
    public Builder sampled(@Nullable Boolean sampled) {
      if (sampled != null) {
        this.flags |= FLAG_SAMPLING_SET;
        if (sampled) {
          this.flags |= FLAG_SAMPLED;
        } else {
          this.flags &= ~FLAG_SAMPLED;
        }
      } else {
        this.flags &= ~FLAG_SAMPLING_SET;
      }
      return this;
    }

    public SpanId build() {
      return new SpanId(this);
    }

    Builder() {
    }
  }

  /** Inspired by {@code okio.Buffer.writeLong} */
  static void writeHexLong(char[] data, int pos, long v) {
    writeHexByte(data, pos + 0,  (byte) ((v >>> 56L) & 0xff));
    writeHexByte(data, pos + 2,  (byte) ((v >>> 48L) & 0xff));
    writeHexByte(data, pos + 4,  (byte) ((v >>> 40L) & 0xff));
    writeHexByte(data, pos + 6,  (byte) ((v >>> 32L) & 0xff));
    writeHexByte(data, pos + 8,  (byte) ((v >>> 24L) & 0xff));
    writeHexByte(data, pos + 10, (byte) ((v >>> 16L) & 0xff));
    writeHexByte(data, pos + 12, (byte) ((v >>> 8L) & 0xff));
    writeHexByte(data, pos + 14, (byte)  (v & 0xff));
  }

  static final char[] HEX_DIGITS =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  static void writeHexByte(char[] data, int pos, byte b) {
    data[pos + 0] = HEX_DIGITS[(b >> 4) & 0xf];
    data[pos + 1] = HEX_DIGITS[b & 0xf];
  }
}
