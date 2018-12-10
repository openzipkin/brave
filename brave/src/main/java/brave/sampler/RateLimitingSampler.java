package brave.sampler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The rate-limited sampler allows you to choose an amount of traces to accept on a per-second
 * interval. The minimum number is 0 and the max is 2,147,483,647 (max int).
 *
 * <p>For example, to allow 10 traces per second, you'd initialize the following:
 * <pre>{@code
 * tracingBuilder.sampler(RateLimitingSampler.create(10));
 * }</pre>
 *
 * <h3>Appropriate Usage</h3>
 *
 * <p>If the rate is 10 or more traces per second, an attempt is made to distribute the accept
 * decisions equally across the second. For example, if the rate is 100, 10 will pass every
 * decisecond as opposed to bunching all pass decisions at the beginning of the second.
 *
 * <p>This sampler is efficient, but not as efficient as the {@link brave.sampler.BoundarySampler}.
 * However, this sampler is insensitive to the trace ID and will operate correctly even if they are
 * not perfectly random.
 *
 * <h3>Implementation</h3>
 *
 * <p>The implementation uses {@link System#nanoTime} and tracks how many yes decisions occur
 * across a second window. When the rate is at least 10/s, the yes decisions are equally split over
 * 10 deciseconds, allowing a roll-over of unused yes decisions up until the end of the second.
 */
public class RateLimitingSampler extends Sampler {
  public static Sampler create(int tracesPerSecond) {
    if (tracesPerSecond < 0) throw new IllegalArgumentException("tracesPerSecond < 0");
    if (tracesPerSecond == 0) return Sampler.NEVER_SAMPLE;
    return new RateLimitingSampler(tracesPerSecond);
  }

  static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
  static final int NANOS_PER_DECISECOND = (int) (NANOS_PER_SECOND / 10);

  final MaxFunction maxFunction;
  final AtomicInteger usage = new AtomicInteger(0);
  final AtomicLong nextReset;

  RateLimitingSampler(int tracesPerSecond) {
    this.maxFunction =
        tracesPerSecond < 10 ? new LessThan10(tracesPerSecond) : new AtLeast10(tracesPerSecond);
    long now = System.nanoTime();
    this.nextReset = new AtomicLong(now + NANOS_PER_SECOND);
  }

  @Override public boolean isSampled(long ignoredTraceId) {
    long now = System.nanoTime();
    long updateAt = nextReset.get();

    long nanosUntilReset = -(now - updateAt); // because nanoTime can be negative
    boolean shouldReset = nanosUntilReset <= 0;
    if (shouldReset) {
      if (nextReset.compareAndSet(updateAt, updateAt + NANOS_PER_SECOND)) {
        usage.set(0);
      }
    }

    int max = maxFunction.max(shouldReset ? 0 : nanosUntilReset);
    int prev, next;
    do { // same form as java 8 AtomicLong.getAndUpdate
      prev = usage.get();
      next = prev + 1;
      if (next > max) return false;
    } while (!usage.compareAndSet(prev, next));
    return true;
  }

  static abstract class MaxFunction {
    /** @param nanosUntilReset zero if was just reset */
    abstract int max(long nanosUntilReset);
  }

  /** For a reservoir of less than 10, we permit draining it completely at any time in the second */
  static final class LessThan10 extends MaxFunction {
    final int tracesPerSecond;

    LessThan10(int tracesPerSecond) {
      this.tracesPerSecond = tracesPerSecond;
    }

    @Override int max(long nanosUntilReset) {
      return tracesPerSecond;
    }
  }

  /**
   * For a reservoir of at least 10, we permit draining up to a decisecond watermark. Because the
   * rate could be odd, we may have a remainder, which is arbitrarily available. We allow any
   * remainders in the 1st decisecond or any time thereafter.
   *
   * <p>Ex. If the rate is 10/s then you can use 1 in the first decisecond, another 1 in the 2nd,
   * or up to 10 by the last.
   *
   * <p>Ex. If the rate is 103/s then you can use 13 in the first decisecond, another 10 in the
   * 2nd, or up to 103 by the last.
   */
  static final class AtLeast10 extends MaxFunction {
    final int[] max;

    AtLeast10(int tracesPerSecond) {
      int tracesPerDecisecond = tracesPerSecond / 10, remainder = tracesPerSecond % 10;
      max = new int[10];
      max[0] = tracesPerDecisecond + remainder;
      for (int i = 1; i < 10; i++) {
        max[i] = max[i - 1] + tracesPerDecisecond;
      }
    }

    @Override int max(long nanosUntilReset) {
      int decisecondsUntilReset = ((int) nanosUntilReset / NANOS_PER_DECISECOND);
      int index = decisecondsUntilReset == 0 ? 0 : 10 - decisecondsUntilReset;
      return max[index];
    }
  }
}
