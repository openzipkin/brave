package brave.sampler;

import com.amazonaws.xray.strategy.sampling.reservoir.Reservoir;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * <p>Brave uses before-the-fact sampling. This means that the decision to keep or drop the trace
 * is made before any work is measured, or annotations are added. As such, the input parameter to
 * Brave samplers is the trace id (64-bit random number).
 *
 * <p>This only tests performance of various approaches against each-other. This doesn't test if
 * the same trace id is consistently sampled or not, or how close to the retention percentage the
 * samplers get.
 *
 * <p>While random sampling gives a better statistical average across all spans, it's less useful
 * than the ability to see end to end interrelated work, such as a from a specific user, or messages
 * blocking others in a queue.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(2)
public class SamplerBenchmarks {

  /**
   * Sample rate is a percentage expressed as a float. So, 0.001 is 0.1% (let one in a 1000nd pass).
   * Zero effectively disables tracing.
   *
   * <p>Here are default sample rates from actual implementations:
   * <pre>
   * <ul>
   *   <li>Finagle Scala Tracer: 0.001</li>
   *   <li>Finagle Ruby Tracer: 0.001</li>
   *   <li>Brave Tracer: 1.0</li>
   *   <li>Zipkin Collector: 1.0</li>
   * </ul>
   * </pre>
   */
  static final float SAMPLE_RATE = 0.01f;

  /**
   * The reservior is a measure of how many traces per second.
   *
   * <p>Here are default sample rates from actual implementations:
   * <pre>
   * <ul>
   *   <li>Amazon X-Ray: 1</li>
   *   <li>New Relic: 1000 spans per minute/20 spans per request (trace)/60 seconds = ~0.8</li>
   * </ul>
   * </pre>
   */
  static final int SAMPLE_RESERVOIR = 1;

  @State(Scope.Benchmark)
  public static class Args {

    /**
     * Arguments include the most negative number, and an arbitrary one.
     */
    // JMH doesn't support Long.MIN_VALUE or hex references, hence the long form literals.
    @Param({"-9223372036854775808", "1234567890987654321"})
    long traceId;
  }

  @Benchmark public boolean sampler_boundary(Args args) {
    return SAMPLER_BOUNDARY.isSampled(args.traceId);
  }

  static final Sampler SAMPLER_BOUNDARY = BoundarySampler.create(SAMPLE_RATE);

  @Benchmark public boolean sampler_counting(Args args) {
    return SAMPLER_RATE.isSampled(args.traceId);
  }

  // Use fixed-seed Random so performance of runs can be compared.
  static final Sampler SAMPLER_RATE = new CountingSampler(SAMPLE_RATE, new Random(1000));

  @Benchmark public boolean sampler_rateLimited_1(Args args) {
    return SAMPLER_RATE_LIMITED.isSampled(args.traceId);
  }

  static final Sampler SAMPLER_RATE_LIMITED = RateLimitingSampler.create(SAMPLE_RESERVOIR);

  @Benchmark public boolean sampler_rateLimited_100(Args args) {
    return SAMPLER_RATE_LIMITED_100.isSampled(args.traceId);
  }

  static final Sampler SAMPLER_RATE_LIMITED_100 = RateLimitingSampler.create(100);

  @Benchmark public boolean sampler_rateLimited_1_xray(Args args) {
    return RESERVOIR_RATE_LIMITED.take();
  }

  static final Reservoir RESERVOIR_RATE_LIMITED = new Reservoir(SAMPLE_RESERVOIR);

  @Benchmark public boolean sampler_rateLimited_100_xray(Args args) {
    return RESERVOIR_RATE_LIMITED_100.take();
  }

  static final Reservoir RESERVOIR_RATE_LIMITED_100 = new Reservoir(100);

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + SamplerBenchmarks.class.getSimpleName() + ".*rate.*")
        .build();

    new Runner(opt).run();
  }
}
