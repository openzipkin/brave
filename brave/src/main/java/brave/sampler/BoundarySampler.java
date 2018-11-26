package brave.sampler;

import java.util.Random;

/**
 * This sampler is appropriate for high-traffic instrumentation (ex edge web servers that each
 * receive >100K requests) who provision random trace ids, and make the sampling decision only once.
 * It defends against nodes in the cluster selecting exactly the same ids.
 *
 * <h3>Implementation</h3>
 *
 * <p>This uses modulo 10000 arithmetic, which allows a minimum sample rate of 0.01%. Trace id
 * collision was noticed in practice in the Twitter front-end cluster. A random salt is here to
 * defend against nodes in the same cluster sampling exactly the same subset of trace ids. The goal
 * was full 64-bit coverage of trace IDs on multi-host deployments.
 *
 * <p>Based on https://github.com/twitter/finagle/blob/b6b1d0414fa24ed0c8bb5112985a4e9c9bcd3c9e/finagle-zipkin-core/src/main/scala/com/twitter/finagle/zipkin/core/Sampler.scala#L68
 */
public final class BoundarySampler extends Sampler {
  static final long SALT = new Random().nextLong();

  /**
   * @param rate 0 means never sample, 1 means always sample. Otherwise minimum sample rate is
   * 0.0001, or 0.01% of traces
   */
  public static Sampler create(float rate) {
    if (rate == 0) return Sampler.NEVER_SAMPLE;
    if (rate == 1.0) return ALWAYS_SAMPLE;
    if (rate < 0.0001f || rate > 1) {
      throw new IllegalArgumentException("rate should be between 0.0001 and 1: was " + rate);
    }
    final long boundary = (long) (rate * 10000); // safe cast as less <= 1
    return new BoundarySampler(boundary);
  }

  private final long boundary;

  BoundarySampler(long boundary) {
    this.boundary = boundary;
  }

  /** Returns true when {@code abs(traceId) <= boundary} */
  @Override
  public boolean isSampled(long traceId) {
    long t = Math.abs(traceId ^ SALT);
    return t % 10000 <= boundary;
  }

  @Override
  public String toString() {
    return "BoundaryTraceIdSampler(" + boundary + ")";
  }
}
