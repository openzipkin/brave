package brave.propagation;

import brave.internal.Nullable;

//@Immutable
public class SamplingFlags {
  static final int FLAG_SAMPLED = 1 << 1;
  static final int FLAG_SAMPLED_SET = 1 << 2;
  static final int FLAG_DEBUG = 1 << 3;

  public static final SamplingFlags EMPTY = new SamplingFlags(0);
  public static final SamplingFlags NOT_SAMPLED = new SamplingFlags(FLAG_SAMPLED_SET);
  public static final SamplingFlags SAMPLED = new SamplingFlags(NOT_SAMPLED.flags | FLAG_SAMPLED);
  public static final SamplingFlags DEBUG = new SamplingFlags(SAMPLED.flags | FLAG_DEBUG);

  /** Allows you to create flags from a boolean value without allocating a builder instance */
  public static SamplingFlags create(@Nullable Boolean sampled, boolean debug) {
    if (debug) return DEBUG;
    if (sampled == null) return EMPTY;
    return sampled ? SAMPLED : NOT_SAMPLED;
  }

  public SamplingFlags build() {
    return flags == 0 ? EMPTY : SamplingFlags.debug(flags) ? DEBUG
        : (flags & FLAG_SAMPLED) == FLAG_SAMPLED ? SAMPLED : NOT_SAMPLED;
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
    return sampled(flags);
  }

  /**
   * True is a request to store this span even if it overrides sampling policy. Defaults to false.
   */
  public final boolean debug() {
    return debug(flags);
  }

  @Override public String toString() {
    return "SamplingFlags(sampled=" + sampled() + ", debug=" + debug() + ")";
  }

  /** @deprecated use {@link #create(Boolean, boolean)}. This will be removed in Brave v6 */
  @Deprecated
  public static final class Builder {
    int flags = 0; // bit field for sampled and debug

    public Builder() {
      // public constructor instead of static newBuilder which would clash with TraceContext's
    }

    public Builder sampled(@Nullable Boolean sampled) {
      if (sampled == null) {
        flags &= ~FLAG_SAMPLED_SET;
        flags &= ~FLAG_SAMPLED;
        return this;
      }
      flags |= FLAG_SAMPLED_SET;
      if (sampled) {
        flags |= FLAG_SAMPLED;
      } else {
        flags &= ~FLAG_SAMPLED;
      }
      return this;
    }

    /** Ensures sampled is set when debug is */
    public Builder debug(boolean debug) {
      if (debug) {
        flags |= FLAG_DEBUG;
        flags |= FLAG_SAMPLED_SET;
        flags |= FLAG_SAMPLED;
      } else {
        flags &= ~FLAG_DEBUG;
      }
      return this;
    }

    /** Allows you to create flags from a boolean value without allocating a builder instance */
    public static SamplingFlags build(@Nullable Boolean sampled) {
      if (sampled != null) return sampled ? SAMPLED : NOT_SAMPLED;
      return EMPTY;
    }

    public SamplingFlags build() {
      return flags == 0 ? EMPTY : SamplingFlags.debug(flags) ? DEBUG
          : (flags & FLAG_SAMPLED) == FLAG_SAMPLED ? SAMPLED : NOT_SAMPLED;
    }
  }

  @Nullable static Boolean sampled(int flags) {
    return (flags & FLAG_SAMPLED_SET) == FLAG_SAMPLED_SET
        ? (flags & FLAG_SAMPLED) == FLAG_SAMPLED
        : null;
  }

  private static boolean debug(int flags) {
    return (flags & FLAG_DEBUG) == FLAG_DEBUG;
  }
}
