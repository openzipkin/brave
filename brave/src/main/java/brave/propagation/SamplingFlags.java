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

  final int flags; // bit field for sampled and debug

  SamplingFlags(int flags) {
    this.flags = flags;
  }

  /**
   * Should we sample this request or not? True means sample, false means don't, null means we defer
   * decision to someone further down in the stack.
   *
   * <p>Note: this is a uniform decision for the entire trace. Advanced sampling patterns can
   * overlay this via {@link Propagation.Factory#isNoop(TraceContext)}. For example, a noop context
   * usually implies sampled is false or unset. However, you can collect data anyway, locally for
   * metrics, or to an aggregation stream.
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
          : SamplingFlags.sampled(flags) ? SAMPLED : NOT_SAMPLED;
    }
  }

  private static Boolean sampled(int flags) {
    return (flags & FLAG_SAMPLED_SET) == FLAG_SAMPLED_SET
        ? (flags & FLAG_SAMPLED) == FLAG_SAMPLED
        : null;
  }

  private static boolean debug(int flags) {
    return (flags & FLAG_DEBUG) == FLAG_DEBUG;
  }
}
