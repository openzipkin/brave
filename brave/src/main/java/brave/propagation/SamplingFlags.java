package brave.propagation;

import brave.internal.InternalPropagation;
import brave.internal.Nullable;
import java.util.List;

import static brave.internal.InternalPropagation.FLAG_DEBUG;
import static brave.internal.InternalPropagation.FLAG_SAMPLED;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_LOCAL;
import static brave.internal.InternalPropagation.FLAG_SAMPLED_SET;

//@Immutable
public class SamplingFlags {
  public static final SamplingFlags EMPTY = new SamplingFlags(0);
  public static final SamplingFlags NOT_SAMPLED = new SamplingFlags(FLAG_SAMPLED_SET);
  public static final SamplingFlags SAMPLED = new SamplingFlags(NOT_SAMPLED.flags | FLAG_SAMPLED);
  public static final SamplingFlags DEBUG = new SamplingFlags(SAMPLED.flags | FLAG_DEBUG);

  static {
    InternalPropagation.instance = new InternalPropagation() {
      @Override public int flags(SamplingFlags flags) {
        return flags.flags;
      }

      @Override
      public TraceContext newTraceContext(int flags, long traceIdHigh, long traceId,
          long localRootId, long parentId, long spanId, List<Object> extra) {
        return new TraceContext(flags, traceIdHigh, traceId, localRootId, parentId, spanId, extra);
      }

      @Override public TraceContext withExtra(TraceContext context, List<Object> extra) {
        return context.withExtra(extra);
      }

      @Override public TraceContext withFlags(TraceContext context, int flags) {
        return context.withFlags(flags);
      }
    };
  }

  final int flags; // bit field for sampled and debug

  SamplingFlags(int flags) {
    this.flags = flags;
  }

  /**
   * Sampled means send span data to Zipkin (or something else compatible with its data). It is a
   * consistent decision for an entire request (trace-scoped). For example, the value should not
   * move from true to false, even if the decision itself can be deferred.
   *
   * <p>Here are the valid options:
   * <pre><ul>
   *   <li>True means the trace is reported, starting with the first span to set the value true</li>
   *   <li>False means the trace should not be reported</li>
   *   <li>Null means the decision should be deferred to the next hop</li>
   * </ul></pre>
   *
   * <p>Once set to true or false, it is expected that this decision is propagated and honored
   * downstream.
   *
   * <p>Note: sampling does not imply the trace is invisible to others. For example, a common
   * practice is to generate and propagate identifiers always. This allows other systems, such as
   * logging, to correlate even when the tracing system has no data.
   */
  @Nullable public final Boolean sampled() {
    return (flags & FLAG_SAMPLED_SET) == FLAG_SAMPLED_SET
        ? (flags & FLAG_SAMPLED) == FLAG_SAMPLED
        : null;
  }

  /**
   * True records this trace locally even if it is not {@link #sampled() sampled downstream}.
   * Defaults to false.
   *
   * <p><em>Note:</em> Setting this does not affect {@link #sampled() remote sampled status}.
   *
   * <p>The use case for this is storing data at other sampling rates, or local aggregation of
   * metrics or service aggregations. How to action this data may or may not require inspection of
   * {@link TraceContext#extra() other propagated information}.
   */
  // Setting this on SamplingFlags object isn't currently supported as there's no obvious use case
  public final boolean sampledLocal() {
    return (flags & FLAG_SAMPLED_LOCAL) == FLAG_SAMPLED_LOCAL;
  }

  /**
   * True implies {@link #sampled()}, and is additionally a request to override any storage or
   * collector layer sampling. Defaults to false.
   */
  public final boolean debug() {
    return debug(flags);
  }

  @Override public String toString() {
    return "SamplingFlags(sampled="
        + sampled()
        + ", sampledLocal="
        + sampledLocal()
        + ", debug="
        + debug()
        + ")";
  }

  /** @deprecated prefer using constants. This will be removed in Brave v6 */
  @Deprecated
  public static final class Builder {
    int flags = 0; // bit field for sampled and debug

    public Builder() {
      // public constructor instead of static newBuilder which would clash with TraceContext's
    }

    /** @see SamplingFlags#sampled() */
    public Builder sampled(@Nullable Boolean sampled) {
      if (sampled == null) {
        flags &= ~(FLAG_SAMPLED_SET | FLAG_SAMPLED);
        return this;
      }
      flags = InternalPropagation.sampled(sampled, flags);
      return this;
    }

    /**
     * Setting debug to true also sets sampled to true.
     *
     * @see SamplingFlags#debug()
     */
    public Builder debug(boolean debug) {
      flags = SamplingFlags.debug(debug, flags);
      return this;
    }

    /** Allows you to create flags from a boolean value without allocating a builder instance */
    public static SamplingFlags build(@Nullable Boolean sampled) {
      if (sampled != null) return sampled ? SAMPLED : NOT_SAMPLED;
      return EMPTY;
    }

    public SamplingFlags build() {
      return toSamplingFlags(flags);
    }
  }

  static boolean debug(int flags) {
    return (flags & FLAG_DEBUG) == FLAG_DEBUG;
  }

  static int debug(boolean debug, int flags) {
    if (debug) {
      flags |= FLAG_DEBUG | FLAG_SAMPLED_SET | FLAG_SAMPLED;
    } else {
      flags &= ~FLAG_DEBUG;
    }
    return flags;
  }

  // Internal: not meant to be used directly by end users
  static final SamplingFlags
      EMPTY_SAMPLED_LOCAL = new SamplingFlags(FLAG_SAMPLED_LOCAL),
      NOT_SAMPLED_SAMPLED_LOCAL = new SamplingFlags(NOT_SAMPLED.flags | FLAG_SAMPLED_LOCAL),
      SAMPLED_SAMPLED_LOCAL = new SamplingFlags(SAMPLED.flags | FLAG_SAMPLED_LOCAL),
      DEBUG_SAMPLED_LOCAL = new SamplingFlags(DEBUG.flags | FLAG_SAMPLED_LOCAL);

  /** This ensures constants are always used, in order to reduce allocation overhead */
  static SamplingFlags toSamplingFlags(int flags) {
    switch (flags) {
      case 0:
        return EMPTY;
      case FLAG_SAMPLED_SET:
        return NOT_SAMPLED;
      case FLAG_SAMPLED_SET | FLAG_SAMPLED:
        return SAMPLED;
      case FLAG_SAMPLED_SET | FLAG_SAMPLED | FLAG_DEBUG:
        return DEBUG;
      case FLAG_SAMPLED_LOCAL:
        return EMPTY_SAMPLED_LOCAL;
      case FLAG_SAMPLED_LOCAL | FLAG_SAMPLED_SET:
        return NOT_SAMPLED_SAMPLED_LOCAL;
      case FLAG_SAMPLED_LOCAL | FLAG_SAMPLED_SET | FLAG_SAMPLED:
        return SAMPLED_SAMPLED_LOCAL;
      case FLAG_SAMPLED_LOCAL | FLAG_SAMPLED_SET | FLAG_SAMPLED | FLAG_DEBUG:
        return DEBUG_SAMPLED_LOCAL;
      default:
        assert false; // programming error, but build anyway
        return new SamplingFlags(flags);
    }
  }
}
