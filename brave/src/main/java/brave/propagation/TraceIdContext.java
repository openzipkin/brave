/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.propagation;

import brave.internal.InternalPropagation;
import brave.internal.Nullable;

import static brave.internal.HexCodec.writeHexLong;
import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;

/**
 * Contains inbound trace ID and sampling flags, used when users control the root trace ID, but not
 * the span ID (ex Amazon X-Ray or other correlation).
 */
//@Immutable
public final class TraceIdContext extends SamplingFlags {
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
    Builder result = new Builder();
    result.flags = flags;
    result.traceIdHigh = traceIdHigh;
    result.traceId = traceId;
    return result;
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

  public static final class Builder {
    long traceIdHigh, traceId;
    int flags;

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
      flags = InternalPropagation.sampled(sampled, flags);
      return this;
    }

    /** @see TraceIdContext#sampled() */
    public Builder sampled(@Nullable Boolean sampled) {
      if (sampled == null) {
        flags &= ~(FLAG_SAMPLED_SET | FLAG_SAMPLED);
        return this;
      }
      return sampled(sampled.booleanValue());
    }

    /** @see TraceIdContext#debug() */
    public Builder debug(boolean debug) {
      flags = SamplingFlags.debug(debug, flags);
      return this;
    }

    public final TraceIdContext build() {
      if (traceId == 0L) throw new IllegalStateException("Missing: traceId");
      return new TraceIdContext(flags, traceIdHigh, traceId);
    }

    Builder() { // no external implementations
    }
  }

  final long traceIdHigh, traceId;

  TraceIdContext(int flags, long traceIdHigh, long traceId) { // no external implementations
    super(flags);
    this.traceIdHigh = traceIdHigh;
    this.traceId = traceId;
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
