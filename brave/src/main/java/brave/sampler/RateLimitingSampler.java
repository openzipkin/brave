package brave.sampler;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Based on https://github.com/aws/aws-xray-sdk-java/blob/2.0.1/aws-xray-recorder-sdk-core/src/main/java/com/amazonaws/xray/strategy/sampling/reservoir/Reservoir.java
 *
 * Uses only Java 6 APIs to be compatible with Brave Core requirements. Accounts for nanoTime()
 * overflow, as unlikely as that might be.
 */
public class RateLimitingSampler extends Sampler {
  public static Sampler create(int tracesPerSecond) {
    if (tracesPerSecond <= 0) {
      return Sampler.NEVER_SAMPLE;
    }
    return new RateLimitingSampler(tracesPerSecond);
  }

  private static long NANOS_PER_SECOND = 1_000_000_000;

  private int tracesPerSecond;
  private AtomicLong usage;
  private AtomicLong nextUpdate;

  RateLimitingSampler(int tracesPerSecond) {
    this.tracesPerSecond = tracesPerSecond;
    this.usage = new AtomicLong(0);
    long now = System.nanoTime();
    this.nextUpdate = new AtomicLong(getNextUpdateValue(now, now));
  }


  @Override public boolean isSampled(long ignoredTraceId) {
    long now = System.nanoTime();
    long updateAt = nextUpdate.get();

    // now is past update time OR
    //      (update time is positive (near Long.MAX_VALUE) AND now is negative (long overflow))
    if (now > updateAt || (updateAt > 0 && now < 0)) {
      if (nextUpdate.compareAndSet(updateAt, getNextUpdateValue(updateAt, now))) {
        usage.set(0);
      }
    }
    return usage.incrementAndGet() <= tracesPerSecond;
  }

  private static long getNextUpdateValue(long previous, long now) {
    long next = previous + NANOS_PER_SECOND;
    // no traces in the last second, so restart the buckets
    // now is past the next update time already OR
    //      next update is positive (near Long.MAX_VALUE) AND now is negative (due to long overflow)
    if (next < now || (next > 0 && now < 0)) {
      return now + NANOS_PER_SECOND;
    }
    return next;
  }
}
