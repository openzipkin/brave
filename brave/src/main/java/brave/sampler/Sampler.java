package brave.sampler;

/**
 * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. whether the
 * overhead of tracing will occur and/or if a trace will be reported to the collection tier.
 *
 * <p>Zipkin v1 uses before-the-fact sampling. This means that the decision to keep or drop the
 * trace is made before any work is measured, or annotations are added. As such, the input parameter
 * to zipkin v1 samplers is the trace ID (lower 64-bits under the assumption all bits are random).
 *
 * <p>The instrumentation sampling decision happens once, at the root of the trace, and is
 * propagated downstream. For this reason, the algorithm needn't be consistent based on trace ID.
 */
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

  /**
   * Returns true if the trace ID should be measured.
   *
   * @param traceId The trace ID to be decided on, can be ignored
   */
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
