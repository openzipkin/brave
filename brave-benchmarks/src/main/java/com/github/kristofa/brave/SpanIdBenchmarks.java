package com.github.kristofa.brave;

import com.twitter.finagle.tracing.TraceId;
import com.twitter.finagle.tracing.TraceId$;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 10, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(1)
public class SpanIdBenchmarks {
  static final SpanId sampledRootSpan =
      new SpanId(1L, 1L, 1L, SpanId.FLAG_SAMPLED | SpanId.FLAG_SAMPLING_SET);
  static final byte[] sampledRootSpanBytes = sampledRootSpan.bytes();
  static final TraceId sampledRootSpanFinagle =
      TraceId$.MODULE$.deserialize(sampledRootSpanBytes).get();

  @Benchmark
  public SpanId fromBytes_brave() {
    return SpanId.fromBytes(sampledRootSpanBytes);
  }

  @Benchmark
  public TraceId fromBytes_finagle() {
    return TraceId$.MODULE$.deserialize(sampledRootSpanBytes).get();
  }

  @Benchmark
  public byte[] bytes_finagle() {
    return TraceId.serialize(sampledRootSpanFinagle);
  }

  @Benchmark
  public byte[] bytes_brave() {
    return sampledRootSpan.bytes();
  }

  @Benchmark
  public String toString_brave() {
    return sampledRootSpan.toString();
  }

  @Benchmark
  public String toString_finagle() {
    return sampledRootSpanFinagle.toString();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + SpanIdBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}
