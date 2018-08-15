package brave.grpc;

import java.util.Collections;
import java.util.Map;
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
public class TagContextBinaryMarshallerBenchmarks {
  static final TagContextBinaryMarshaller marshaller = new TagContextBinaryMarshaller();

  static final Map<String, String>
      context = Collections.singletonMap("method", "helloworld.Greeter/SayHello");

  static final byte[] serialized = marshaller.toBytes(context);

  @Benchmark public byte[] toBytes() {
    return marshaller.toBytes(context);
  }

  @Benchmark public Map<String, String> parseBytes() {
    return marshaller.parseBytes(serialized);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + TagContextBinaryMarshallerBenchmarks.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }
}
