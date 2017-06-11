package com.github.kristofa.brave;

/**
 * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. whether the
 * overhead of tracing will occur and/or if a trace will be reported to the collection tier.
 *
 * <p>Zipkin v1 uses before-the-fact sampling. This means that the decision to keep or drop the
 * trace is made before any work is measured, or annotations are added. As such, the input parameter
 * to zipkin v1 samplers is the trace ID (64-bit random number).
 *
 * <p>The instrumentation sampling decision happens once, at the root of the trace, and is
 * propagated downstream. For this reason, the decision needn't be consistent based on trace ID.
 * @deprecated Replaced by {@code brave.sampler.Sampler}
 */
@Deprecated
// abstract for factory-method support on Java language level 7
public abstract class Sampler {

  public static final Sampler ALWAYS_SAMPLE = new Sampler() {
    @Override public boolean isSampled(long traceId) {
      return true;
    }

    @Override public String toString() {
      return "AlwaysSample";
    }
  };

  public static final Sampler NEVER_SAMPLE = new Sampler() {
    @Override public boolean isSampled(long traceId) {
      return false;
    }

    @Override public String toString() {
      return "NeverSample";
    }
  };

  /** Returns true if the trace ID should be measured. */
  public abstract boolean isSampled(long traceId);

  /**
   * Returns a sampler, given a rate expressed as a percentage.
   *
   * <p>The sampler returned is good for low volumes of traffic (<100K requests), as it is precise.
   * If you have high volumes of traffic, consider {@link BoundarySampler}.
   *
   * @param rate minimum sample rate is 0.01, or 1% of traces
   */
  public static Sampler create(float rate) {
    return CountingSampler.create(rate);
  }
}
