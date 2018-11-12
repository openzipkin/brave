package brave.sampler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Based on https://github.com/aws/aws-xray-sdk-java/blob/2.0.1/aws-xray-recorder-sdk-core/src/main/java/com/amazonaws/xray/strategy/sampling/reservoir/Reservoir.java
 *
 * Uses only Java 6 APIs to be compatible with Brave Core requirements. Accounts for nanoTime()
 * overflow, as unlikely as that might be.
 */
public class RateLimitingSampler extends Sampler {
  public static Sampler create(int tracesPerSecond) {
    if (tracesPerSecond < 0) throw new IllegalArgumentException("tracesPerSecond < 0");
    if (tracesPerSecond == 0) return Sampler.NEVER_SAMPLE;
    return new RateLimitingSampler(tracesPerSecond);
  }

  static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

  final int tracesPerSecond;
  final AtomicInteger usage = new AtomicInteger(0);
  final AtomicLong nextUpdate;

  RateLimitingSampler(int tracesPerSecond) {
    this.tracesPerSecond = tracesPerSecond;
    long now = System.nanoTime();
    this.nextUpdate = new AtomicLong(now + NANOS_PER_SECOND);
  }

  @Override public boolean isSampled(long ignoredTraceId) {
    long now = System.nanoTime();
    long updateAt = nextUpdate.get();

    if (now - updateAt >= 0) {
      if (nextUpdate.compareAndSet(updateAt, updateAt + NANOS_PER_SECOND)) {
        usage.set(0);
      }
    }
    return usage.incrementAndGet() <= tracesPerSecond;
  }
}
