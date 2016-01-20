package com.github.kristofa.brave;

/**
 * Sampler is responsible for deciding if a particular trace should be "sampled", i.e. recorded in
 * permanent storage.
 *
 * <p>Zipkin v1 uses before-the-fact sampling. This means that the decision to keep or drop the
 * trace is made before any work is measured, or annotations are added. As such, the input parameter
 * to zipkin v1 samplers is the trace ID (64-bit random number).
 */
// abstract for factory-method support on Java language level 7
public abstract class Sampler {

  /** Returns true if the trace ID should be recorded. */
  public abstract boolean isSampled(long traceId);

  /**
   * Returns a sampler, given a rate expressed as a percentage.
   *
   * @param rate minimum sample rate is 0.0001, or 0.01% of traces
   */
  public static Sampler create(float rate) {
    return new ZipkinSampler(rate);
  }

  static final class ZipkinSampler extends Sampler {

    private final zipkin.Sampler delegate;

    ZipkinSampler(float rate) {
      this.delegate = zipkin.Sampler.create(rate);
    }

    @Override
    public boolean isSampled(long traceId) {
      return delegate.isSampled(traceId);
    }
  }
}
