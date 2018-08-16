package brave.grpc;

import brave.internal.HexCodec;
import brave.propagation.TraceContext;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class TraceContextBinaryMarshallerBenchmarks {
  static final TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef01"))
      .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
      .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
      .sampled(true)
      .build();

  static final byte[] serialized = TraceContextBinaryFormat.toBytes(context);

  @Benchmark public byte[] toBytes() {
    return TraceContextBinaryFormat.toBytes(context);
  }

  @Benchmark public TraceContext parseBytes() {
    return TraceContextBinaryFormat.parseBytes(serialized, null);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + TraceContextBinaryMarshallerBenchmarks.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }
}
