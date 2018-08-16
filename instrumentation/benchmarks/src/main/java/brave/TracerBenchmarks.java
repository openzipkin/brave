package brave;

import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.reporter.Reporter;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@State(Scope.Benchmark)
public class TracerBenchmarks {

  TraceContext context =
      TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).sampled(true).build();
  TraceContext unsampledContext =
      TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).sampled(false).build();
  TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(context);
  TraceContextOrSamplingFlags unsampledExtracted =
      TraceContextOrSamplingFlags.create(SamplingFlags.NOT_SAMPLED);

  Tracer tracer;

  @Setup(Level.Trial) public void init() {
    tracer = Tracing.newBuilder().spanReporter(Reporter.NOOP).build().tracer();
  }

  @TearDown(Level.Trial) public void close() {
    Tracing.current().close();
  }

  @Benchmark public void startScopedSpanWithParent() {
    startScopedSpanWithParent(context);
  }

  @Benchmark public void startScopedSpanWithParent_unsampled() {
    startScopedSpanWithParent(unsampledContext);
  }

  void startScopedSpanWithParent(TraceContext context) {
    ScopedSpan span = tracer.startScopedSpanWithParent("encode", context);
    try {
      span.tag("foo", "bar");
      span.tag("baz", "qux");
    } finally {
      span.finish();
    }
  }

  @Benchmark public void newChildWithSpanInScope() {
    newChildWithSpanInScope(context);
  }

  @Benchmark public void newChildWithSpanInScope_unsampled() {
    newChildWithSpanInScope(unsampledContext);
  }

  void newChildWithSpanInScope(TraceContext context) {
    Span span = tracer.newChild(context).name("encode").start();
    try (Tracer.SpanInScope scope = tracer.withSpanInScope(span)) {
      span.tag("foo", "bar");
      span.tag("baz", "qux");
    } finally {
      span.finish();
    }
  }

  @Benchmark public void joinWithSpanInScope() {
    joinWithSpanInScope(context);
  }

  @Benchmark public void joinWithSpanInScope_unsampled() {
    joinWithSpanInScope(unsampledContext);
  }

  void joinWithSpanInScope(TraceContext context) {
    Span span = tracer.joinSpan(context).name("encode").start();
    try (Tracer.SpanInScope scope = tracer.withSpanInScope(span)) {
      span.tag("foo", "bar");
      span.tag("baz", "qux");
    } finally {
      span.finish();
    }
  }

  @Benchmark public void nextWithSpanInScope() {
    nextWithSpanInScope(extracted);
  }

  @Benchmark public void nextWithSpanInScope_unsampled() {
    nextWithSpanInScope(unsampledExtracted);
  }

  void nextWithSpanInScope(TraceContextOrSamplingFlags extracted) {
    Span span = tracer.nextSpan(extracted).name("encode").start();
    try (Tracer.SpanInScope scope = tracer.withSpanInScope(span)) {
      span.tag("foo", "bar");
      span.tag("baz", "qux");
    } finally {
      span.finish();
    }
  }

  // Convenience main entry-point
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
        .addProfiler("gc")
        .include(".*" + TracerBenchmarks.class.getSimpleName() + ".*")
        .build();

    new Runner(opt).run();
  }
}
