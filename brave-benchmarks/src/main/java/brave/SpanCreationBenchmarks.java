package brave;

import brave.internal.recorder.Recorder;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.LocalTracer;
import com.twitter.zipkin.gen.Endpoint;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import zipkin.Constants;
import zipkin.TraceKeys;
import zipkin.reporter.Reporter;

import static brave.Span.Kind.CLIENT;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class SpanCreationBenchmarks {

  Tracer tracer;
  Recorder recorder;
  Clock clock;
  Brave brave;

  @Setup
  public void setup() {
    // real everything except reporting
    tracer = Tracer.newBuilder()
        .reporter(Reporter.NOOP)
        .build();
    recorder = tracer.recorder;
    clock = tracer.clock;
    brave = new Brave.Builder()
        .reporter(Reporter.NOOP)
        .build();
  }

  @Benchmark
  public LocalTracer simpleRootSpan_brave3() {
    LocalTracer tracer = brave.localTracer();
    tracer.startNewSpan("codec", "encode");
    try {
      return tracer; // pretend we are doing codec work
    } finally {
      tracer.finishSpan();
    }
  }

  @Benchmark
  public ClientTracer elaborateSpan_brave3() {
    ClientTracer tracer = brave.clientTracer();

    tracer.startNewSpan("getOrCreate");
    tracer.submitBinaryAnnotation("clnt/finagle.version", "6.36.0");
    tracer.submitBinaryAnnotation(TraceKeys.HTTP_PATH, "/api");

    tracer.setClientSent(Endpoint.builder() // implicit start
        .serviceName("backend")
        .ipv4(127 << 24 | 1)
        .port(8080).build());
    tracer.submitAnnotation(Constants.WIRE_SEND);
    tracer.submitAnnotation(Constants.WIRE_RECV);
    tracer.setClientReceived(); // implicit finish

    return tracer;
  }

  @Benchmark
  public Span simpleRootSpan_brave4() {
    Span span = tracer.newTrace().name("encode").start();
    span.finish();
    return span; // to satisfy the signature
  }

  @Benchmark
  public Span elaborateSpan_brave4() {
    Span span = tracer.newTrace().kind(CLIENT).name("getOrCreate");

    span.tag("clnt/finagle.version", "6.36.0");
    span.tag(TraceKeys.HTTP_PATH, "/api");
    span.remoteEndpoint(zipkin.Endpoint.builder()
        .serviceName("backend")
        .ipv4(127 << 24 | 1)
        .port(8080).build());

    span.start();
    span.annotate(Constants.WIRE_SEND);
    span.annotate(Constants.WIRE_RECV);
    span.finish();

    return span;
  }

  @Benchmark
  public Recorder simpleRootSpan_brave4_recorder() {
    TraceContext context = tracer.nextContext(null, SamplingFlags.SAMPLED);
    recorder.name(context, "encode");
    recorder.start(context, clock.currentTimeMicroseconds());
    recorder.finish(context, clock.currentTimeMicroseconds());
    return recorder;
  }

  @Benchmark
  public Recorder elaborateSpan_brave4_recorder() {
    TraceContext context = tracer.nextContext(null, SamplingFlags.SAMPLED);
    recorder.kind(context, CLIENT);
    recorder.name(context, "getOrCreate");
    recorder.tag(context, "clnt/finagle.version", "6.36.0");
    recorder.tag(context, TraceKeys.HTTP_PATH, "/api");
    recorder.remoteEndpoint(context, zipkin.Endpoint.builder()
        .serviceName("backend")
        .ipv4(127 << 24 | 1)
        .port(8080).build());

    recorder.start(context, clock.currentTimeMicroseconds());
    recorder.annotate(context, clock.currentTimeMicroseconds(), Constants.WIRE_SEND);
    recorder.annotate(context, clock.currentTimeMicroseconds(), Constants.WIRE_RECV);
    recorder.finish(context, clock.currentTimeMicroseconds());

    return recorder;
  }

  @Benchmark
  public Span newTrace_brave4() {
    return tracer.newTrace();
  }
  // TODO: add comparisons for joinSpan (ex server collaborates with client-originated span
}
