package brave.spring.rabbit;

import brave.Tracing;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import zipkin2.reporter.Reporter;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class TracingMessagePostProcessorBenchmarks {
  Message message = MessageBuilder.withBody(new byte[0]).build();
  TracingMessagePostProcessor tracingPostProcessor, tracingB3SinglePostProcessor;

  @Setup(Level.Trial) public void init() {
    Tracing tracing = Tracing.newBuilder().spanReporter(Reporter.NOOP).build();
    tracingPostProcessor = new TracingMessagePostProcessor(SpringRabbitTracing.create(tracing));
    tracingB3SinglePostProcessor = new TracingMessagePostProcessor(
        SpringRabbitTracing.newBuilder(tracing).writeB3SingleFormat(true).build()
    );
  }

  @TearDown(Level.Trial) public void close() {
    Tracing.current().close();
  }

  @Benchmark public Message send_traced() {
    return tracingPostProcessor.postProcessMessage(message);
  }

  @Benchmark public Message send_traced_b3Single() {
    return tracingB3SinglePostProcessor.postProcessMessage(message);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + TracingMessagePostProcessorBenchmarks.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }
}
