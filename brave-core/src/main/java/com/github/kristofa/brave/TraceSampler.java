package com.github.kristofa.brave;

/**
 * Zipkin v1 uses before-the-fact sampling. This means that the decision to keep or drop the trace
 * is made before any work is measured, or annotations are added. As such, the input parameter to
 * zipkin v1 samplers is the trace id (64-bit random number).
 *
 * <p>The implementation is based on zipkin-java's TraceIdSampler. It is percentage based, and its
 * accuracy is tied to distribution of traceIds across 64bits. For example, tests have shown an
 * error rate of 3% when traceIds are random. It is idempotent, ie a consistent decision for a given
 * trace id.
 */
// abstract for factory-method support on Java language level 7
public abstract class TraceSampler {

  /**
   * Returns true if the traceId should be retained.
   */
  public abstract boolean test(long traceId);

  /**
   * Returns a constant sampler, given a rate expressed as a percentage.
   *
   * @param rate minimum sample rate is 0.0001, or 0.01% of traces
   */
  public static TraceSampler create(float rate) {
    return new ZipkinTraceSampler(rate);
  }

  static final class ZipkinTraceSampler extends TraceSampler {

    private final io.zipkin.TraceIdSampler delegate;

    ZipkinTraceSampler(float rate) {
      this.delegate = io.zipkin.TraceIdSampler.create(rate);
    }

    @Override
    public boolean test(long traceId) {
      return delegate.test(traceId);
    }
  }
}
