package brave.sampler;

import java.util.concurrent.atomic.AtomicLong;

public class RateLimitingSampler extends Sampler {
  public static Sampler create(int tracesPerSecond) {
    if (tracesPerSecond <= 0) {
      return Sampler.NEVER_SAMPLE;
    }
    return new RateLimitingSampler(tracesPerSecond);
  }

  private int tracesPerSecond;
  private AtomicLong usage;
  private long thisSecond;

  RateLimitingSampler(int tracesPerSecond) {
    this.tracesPerSecond = tracesPerSecond;
    this.usage = new AtomicLong(0);
    this.thisSecond = currentSecond();
  }


  @Override public boolean isSampled(long traceId) {
    long now = currentSecond();
    if (now != thisSecond) {
      usage.set(0);
      thisSecond = now;
    }
    return usage.getAndIncrement() < tracesPerSecond;
  }

  private long currentSecond() {
    return System.currentTimeMillis() / 1000;
  }
}
