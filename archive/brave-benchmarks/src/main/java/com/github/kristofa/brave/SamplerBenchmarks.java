package com.github.kristofa.brave;

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
 * <p>Brave 3 uses before-the-fact sampling. This means that the decision to keep or drop the trace
 * is made before any work is measured, or annotations are added. As such, the input parameter to
 * Brave 3 samplers is the trace id (64-bit random number).
 *
 * <p>This only tests performance of various approaches against each-other. This doesn't test if the
 * same trace id is consistently sampled or not, or how close to the retention percentage the
 * samplers get.
 *
 * <p>While random sampling gives a better statistical average across all spans, it's less useful
 * than the ability to see end to end interrelated work, such as a from a specific user, or messages
 * blocking others in a queue. More sampling patterns are expected in OpenTracing and Zipkin v2.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
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
   *   <li>Brave Java Tracer: 1.0</li>
   *   <li>Zipkin Collector: 1.0</li>
   * </ul>
   * </pre>
   */
  static final float SAMPLE_RATE = 0.01f;

  @State(Scope.Benchmark)
  public static class Args {

    /**
     * Arguments include the most negative number, and an arbitrary one.
     */
    // JMH doesn't support Long.MIN_VALUE or hex references, hence the long form literals.
    @Param({"-9223372036854775808", "1234567890987654321"})
    long traceId;
  }

  /**
   * This measures the boundary trace id sampler provided with brave-core
   */
  @Benchmark
  public boolean sampler_boundary(Args args) {
    return TRACE_ID_SAMPLER_BOUNDARY.isSampled(args.traceId);
  }

  static final Sampler TRACE_ID_SAMPLER_BOUNDARY = BoundarySampler.create(SAMPLE_RATE);

  /**
   * This measures the counting trace id sampler provided with brave-core
   */
  @Benchmark
  public boolean sampler_counting(Args args) {
    return TRACE_ID_SAMPLER_COUNTING.isSampled(args.traceId);
  }

  static final Sampler TRACE_ID_SAMPLER_COUNTING = CountingSampler.create(SAMPLE_RATE);

  /**
   * Finagle's scala sampler samples using modulo 10000 arithmetic, which allows a minimum sample
   * rate of 0.01%.
   *
   * <p>This function is only invoked once per trace, propagating the result downstream as a field
   * called sampled. This means it is designed for instrumented entry-points.
   *
   * <p>Trace id collision was noticed in practice in the Twitter front-end cluster. A random salt
   * feature was added to defend against nodes in the same cluster sampling exactly the same subset
   * of trace ids. The goal was full 64-bit coverage of traceIds on multi-host deployments.
   *
   * <p>See https://github.com/twitter/finagle/blob/develop/finagle-zipkin/src/main/scala/com/twitter/finagle/zipkin/thrift/Sampler.scala#L68
   */
  @Benchmark
  public boolean compare_modulo10000_salted(Args args) {
    long traceId = args.traceId;
    long t = Math.abs(traceId ^ SALT);
    // Minimum sample rate is one in 10000, or 0.01% of traces
    return t % 10000 < SAMPLE_RATE * 10000; // Constant expression for readability
  }

  static final long SALT = new Random().nextLong();

  /**
   * Finagle's ruby sampler gets a random number and compares that against the sample rate.
   *
   * <p>This function is only invoked once per trace, propagating the result downstream as a field
   * called sampled. This means it is designed for instrumented entry-points.
   *
   * <p>See https://github.com/twitter/finagle/blob/develop/finagle-thrift/src/main/ruby/lib/finagle-thrift/trace.rb#L135
   */
  @Benchmark
  public boolean compareRandomNumber(Args args) {
    return RNG.nextFloat() < SAMPLE_RATE; // notice trace id is not used
  }

  final Random RNG = new Random();

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + SamplerBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}
