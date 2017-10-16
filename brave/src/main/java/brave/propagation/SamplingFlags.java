package brave.propagation;

import brave.internal.Nullable;

//@Immutable
public abstract class SamplingFlags {
  public static final SamplingFlags EMPTY = new SamplingFlagsImpl(null, false);
  public static final SamplingFlags SAMPLED = new SamplingFlagsImpl(true, false);
  public static final SamplingFlags NOT_SAMPLED = new SamplingFlagsImpl(false, false);
  public static final SamplingFlags DEBUG = new SamplingFlagsImpl(true, true);

  /**
   * Should we sample this request or not? True means sample, false means don't, null means we defer
   * decision to someone further down in the stack.
   */
  @Nullable public abstract Boolean sampled();

  /**
   * True is a request to store this span even if it overrides sampling policy. Defaults to false.
   */
  public abstract boolean debug();

  public static final class Builder {
    Boolean sampled;
    boolean debug = false;

    public Builder() {
      // public constructor instead of static newBuilder which would clash with TraceContext's
    }

    public Builder sampled(@Nullable Boolean sampled) {
      this.sampled = sampled;
      return this;
    }

    public Builder debug(boolean debug) {
      this.debug = debug;
      if (debug) sampled(true);
      return this;
    }

    /** Allows you to create flags from a boolean value without allocating a builder instance */
    public static SamplingFlags build(@Nullable Boolean sampled) {
      if (sampled != null) return sampled ? SAMPLED : NOT_SAMPLED;
      return EMPTY;
    }

    public SamplingFlags build() {
      if (debug) return DEBUG;
      return build(sampled);
    }
  }

  static final class SamplingFlagsImpl extends SamplingFlags {
    final Boolean sampled;
    final boolean debug;

    SamplingFlagsImpl(Boolean sampled, boolean debug) {
      this.sampled = sampled;
      this.debug = debug;
    }

    @Override public Boolean sampled() {
      return sampled;
    }

    @Override public boolean debug() {
      return debug;
    }

    @Override public String toString() {
      return "SamplingFlags(sampled=" + sampled + ", debug=" + debug + ")";
    }
  }

  SamplingFlags() {
  }
}
