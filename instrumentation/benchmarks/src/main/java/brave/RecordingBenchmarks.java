package brave;

import brave.internal.Internal;
import java.io.IOException;
import java.util.List;
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
import zipkin.TraceKeys;
import zipkin.internal.v2.Endpoint;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Callback;
import zipkin.reporter.Encoding;
import zipkin.reporter.Sender;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@State(Scope.Benchmark)
public class RecordingBenchmarks {
  static final Sender NOOP_JSON_SENDER = new Sender() {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public int messageMaxBytes() {
      return 5 * 1024;
    }

    @Override public int messageSizeInBytes(List<byte[]> list) {
      return Encoding.JSON.listSizeInBytes(list);
    }

    @Override public void sendSpans(List<byte[]> list, Callback callback) {
      callback.onComplete();
    }

    @Override public CheckResult check() {
      return CheckResult.OK;
    }

    @Override public void close() throws IOException {
    }
  };

  Tracing tracingV1;
  Tracing tracingV2;

  @Setup(Level.Trial) public void init() throws Exception {
    tracingV1 = Tracing.newBuilder()
        .reporter(AsyncReporter.builder(NOOP_JSON_SENDER).build())
        .build();
    Tracing.Builder builder = Tracing.newBuilder();
    Internal.instance.v2Reporter(builder, AsyncReporter.builder(NOOP_JSON_SENDER));
    tracingV2 = builder.build();
  }

  @TearDown(Level.Trial) public void close() throws Exception {
    tracingV1.close();
    tracingV2.close();
  }

  @Benchmark public void spanv1() throws Exception {
    tracingV1.tracer().newTrace().name("get")
        .kind(Span.Kind.SERVER).start()
        .v2RemoteEndpoint(Endpoint.newBuilder().ip("2001:db8::c001").build())
        .tag(TraceKeys.HTTP_PATH, "/backend")
        .tag("srv/finagle.version", "6.44.0")
        .finish();
  }

  @Benchmark public void spanv2() throws Exception {
    tracingV2.tracer().newTrace().name("get")
        .kind(Span.Kind.SERVER).start()
        .v2RemoteEndpoint(Endpoint.newBuilder().ip("2001:db8::c001").build())
        .tag(TraceKeys.HTTP_PATH, "/backend")
        .tag("srv/finagle.version", "6.44.0")
        .finish();
  }
}
