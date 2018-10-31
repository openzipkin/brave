package brave.sampler;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Based on https://github.com/aws/aws-xray-sdk-java/blob/2.0.1/aws-xray-recorder-sdk-core/src/main/java/com/amazonaws/xray/strategy/sampling/reservoir/Reservoir.java
 *
 * Uses only Java 6 APIs to be compatible with Brave Core requirements
 */
public class RateLimitingSampler extends Sampler {
  public static Sampler create(int tracesPerSecond) {
    if (tracesPerSecond <= 0) {
      return Sampler.NEVER_SAMPLE;
    }
    return new RateLimitingSampler(tracesPerSecond);
  }

  private int tracesPerSecond;
  private AtomicLong usage;
  private AtomicLong nextUpdate;

  RateLimitingSampler(int tracesPerSecond) {
    this.tracesPerSecond = tracesPerSecond;
    this.usage = new AtomicLong(0);
    this.nextUpdate = new AtomicLong(getNextUpdateValue());
  }


  @Override public boolean isSampled(long traceId) {
    long now = System.nanoTime();
    if ((now < 0 && nextUpdate.get() > 0) // nanoTime wrapped since the last sample
        || now >= nextUpdate.get()) {
      usage.set(1);
      nextUpdate.set(getNextUpdateValue());
      return true;
    }
    return usage.getAndIncrement() < tracesPerSecond;
  }

  private long getNextUpdateValue() {
    return ((System.nanoTime() / 1_000_000_000) + 1) * 1_000_000_000;
  }
}
