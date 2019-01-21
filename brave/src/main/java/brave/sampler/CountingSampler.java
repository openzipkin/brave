package brave.sampler;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This sampler is appropriate for low-traffic instrumentation (ex servers that each receive <100K
 * requests), or those who do not provision random trace ids. It is not appropriate for collectors
 * as the sampling decision isn't idempotent (consistent based on trace id).
 *
 * <h3>Implementation</h3>
 *
 * <p>This initializes a random bitset of size 100 (corresponding to 1% granularity). This means
 * that it is accurate in units of 100 traces. At runtime, this loops through the bitset, returning
 * the value according to a counter.
 */
public final class CountingSampler extends Sampler {

  /**
   * @param rate 0 means never sample, 1 means always sample. Otherwise minimum sample rate is 0.01,
   * or 1% of traces
   */
  public static Sampler create(final float rate) {
    if (rate == 0) return NEVER_SAMPLE;
    if (rate == 1.0) return ALWAYS_SAMPLE;
    if (rate < 0.01f || rate > 1) {
      throw new IllegalArgumentException("rate should be between 0.01 and 1: was " + rate);
    }
    return new CountingSampler(rate);
  }

  private final AtomicInteger counter;
  private final BitSet sampleDecisions;

  /** Fills a bitset with decisions according to the supplied rate. */
  CountingSampler(float rate) {
    this(rate, new Random());
  }

  /**
   * Fills a bitset with decisions according to the supplied rate with the supplied {@link Random}.
   */
  CountingSampler(float rate, Random random) {
    counter = new AtomicInteger();
    int outOf100 = (int) (rate * 100.0f);
    this.sampleDecisions = randomBitSet(100, outOf100, random);
  }

  /** loops over the pre-canned decisions, resetting to zero when it gets to the end. */
  @Override
  public boolean isSampled(long traceIdIgnored) {
    return sampleDecisions.get(mod(counter.getAndIncrement(), 100));
  }

  @Override
  public String toString() {
    return "CountingSampler()";
  }

  /**
   * Returns a non-negative mod.
   */
  static int mod(int dividend, int divisor) {
    int result = dividend % divisor;
    return result >= 0 ? result : divisor + result;
  }

  /**
   * Reservoir sampling algorithm borrowed from Stack Overflow.
   *
   * http://stackoverflow.com/questions/12817946/generate-a-random-bitset-with-n-1s
   */
  static BitSet randomBitSet(int size, int cardinality, Random rnd) {
    BitSet result = new BitSet(size);
    int[] chosen = new int[cardinality];
    int i;
    for (i = 0; i < cardinality; ++i) {
      chosen[i] = i;
      result.set(i);
    }
    for (; i < size; ++i) {
      int j = rnd.nextInt(i + 1);
      if (j < cardinality) {
        result.clear(chosen[j]);
        result.set(i);
        chosen[j] = i;
      }
    }
    return result;
  }
}
