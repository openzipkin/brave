/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ExtraFieldPropagation;
import brave.propagation.Propagation;
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
  Propagation.Factory extraFactory = ExtraFieldPropagation.newFactory(
    B3Propagation.FACTORY, "x-vcap-request-id");

  TraceContext context =
    TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).sampled(true).build();
  TraceContext contextExtra = extraFactory.decorate(context);
  TraceContext unsampledContext =
    TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).sampled(false).build();
  TraceContext unsampledContextExtra = extraFactory.decorate(unsampledContext);
  TraceContext sampledLocalContext = unsampledContext.toBuilder().sampledLocal(true).build();
  TraceContext sampledLocalContextExtra = extraFactory.decorate(sampledLocalContext);
  TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(context);
  TraceContextOrSamplingFlags extractedExtra = TraceContextOrSamplingFlags.create(contextExtra);
  TraceContextOrSamplingFlags unsampledExtracted =
    TraceContextOrSamplingFlags.create(SamplingFlags.NOT_SAMPLED);
  TraceContextOrSamplingFlags unsampledExtractedExtra =
    TraceContextOrSamplingFlags.newBuilder()
      .samplingFlags(SamplingFlags.NOT_SAMPLED)
      .addExtra(contextExtra.extra())
      .build();

  Tracer tracer;
  Tracer tracerExtra;

  @Setup(Level.Trial) public void init() {
    tracer = Tracing.newBuilder()
      .addFinishedSpanHandler(new FinishedSpanHandler() {
        @Override public boolean handle(TraceContext context, MutableSpan span) {
          return true; // anonymous subtype prevents all recording from being no-op
        }
      })
      .spanReporter(Reporter.NOOP).build().tracer();
    tracerExtra = Tracing.newBuilder()
      .propagationFactory(ExtraFieldPropagation.newFactory(
        B3Propagation.FACTORY, "x-vcap-request-id"))
      .addFinishedSpanHandler(new FinishedSpanHandler() {
        @Override public boolean handle(TraceContext context, MutableSpan span) {
          return true; // anonymous subtype prevents all recording from being no-op
        }
      })
      .spanReporter(Reporter.NOOP).build().tracer();
  }

  @TearDown(Level.Trial) public void close() {
    Tracing.current().close();
  }

  @Benchmark public void startScopedSpanWithParent() {
    startScopedSpanWithParent(tracer, context);
  }

  @Benchmark public void startScopedSpanWithParent_extra() {
    startScopedSpanWithParent(tracerExtra, contextExtra);
  }

  @Benchmark public void startScopedSpanWithParent_unsampled() {
    startScopedSpanWithParent(tracer, unsampledContext);
  }

  @Benchmark public void startScopedSpanWithParent_unsampled_extra() {
    startScopedSpanWithParent(tracerExtra, unsampledContextExtra);
  }

  @Benchmark public void startScopedSpanWithParent_sampledLocal() {
    startScopedSpanWithParent(tracer, sampledLocalContext);
  }

  @Benchmark public void startScopedSpanWithParent_sampledLocal_extra() {
    startScopedSpanWithParent(tracerExtra, sampledLocalContextExtra);
  }

  void startScopedSpanWithParent(Tracer tracer, TraceContext context) {
    ScopedSpan span = tracer.startScopedSpanWithParent("encode", context);
    try {
      span.tag("foo", "bar");
      span.tag("baz", "qux");
    } finally {
      span.finish();
    }
  }

  @Benchmark public void newChildWithSpanInScope() {
    newChildWithSpanInScope(tracer, context);
  }

  @Benchmark public void newChildWithSpanInScope_extra() {
    newChildWithSpanInScope(tracerExtra, contextExtra);
  }

  @Benchmark public void newChildWithSpanInScope_unsampled() {
    newChildWithSpanInScope(tracer, unsampledContext);
  }

  @Benchmark public void newChildWithSpanInScope_unsampled_extra() {
    newChildWithSpanInScope(tracerExtra, unsampledContextExtra);
  }

  @Benchmark public void newChildWithSpanInScope_sampledLocal() {
    newChildWithSpanInScope(tracer, sampledLocalContext);
  }

  @Benchmark public void newChildWithSpanInScope_sampledLocal_extra() {
    newChildWithSpanInScope(tracerExtra, sampledLocalContextExtra);
  }

  void newChildWithSpanInScope(Tracer tracer, TraceContext context) {
    Span span = tracer.newChild(context).name("encode").start();
    try (Tracer.SpanInScope scope = tracer.withSpanInScope(span)) {
      span.tag("foo", "bar");
      span.tag("baz", "qux");
    } finally {
      span.finish();
    }
  }

  @Benchmark public void joinWithSpanInScope() {
    joinWithSpanInScope(tracer, context);
  }

  @Benchmark public void joinWithSpanInScope_extra() {
    joinWithSpanInScope(tracerExtra, contextExtra);
  }

  @Benchmark public void joinWithSpanInScope_unsampled() {
    joinWithSpanInScope(tracer, unsampledContext);
  }

  @Benchmark public void joinWithSpanInScope_unsampled_extra() {
    joinWithSpanInScope(tracerExtra, unsampledContextExtra);
  }

  @Benchmark public void joinWithSpanInScope_sampledLocal() {
    joinWithSpanInScope(tracer, sampledLocalContext);
  }

  @Benchmark public void joinWithSpanInScope_sampledLocal_extra() {
    joinWithSpanInScope(tracerExtra, sampledLocalContextExtra);
  }

  void joinWithSpanInScope(Tracer tracer, TraceContext context) {
    Span span = tracer.joinSpan(context).name("encode").start();
    try (Tracer.SpanInScope scope = tracer.withSpanInScope(span)) {
      span.tag("foo", "bar");
      span.tag("baz", "qux");
    } finally {
      span.finish();
    }
  }

  @Benchmark public void nextWithSpanInScope() {
    nextWithSpanInScope(tracer, extracted);
  }

  @Benchmark public void nextWithSpanInScope_extra() {
    nextWithSpanInScope(tracerExtra, extractedExtra);
  }

  @Benchmark public void nextWithSpanInScope_unsampled() {
    nextWithSpanInScope(tracer, unsampledExtracted);
  }

  @Benchmark public void nextWithSpanInScope_unsampled_extra() {
    nextWithSpanInScope(tracerExtra, unsampledExtractedExtra);
  }

  void nextWithSpanInScope(Tracer tracer, TraceContextOrSamplingFlags extracted) {
    Span span = tracer.nextSpan(extracted).name("encode").start();
    try (Tracer.SpanInScope scope = tracer.withSpanInScope(span)) {
      span.tag("foo", "bar");
      span.tag("baz", "qux");
    } finally {
      span.finish();
    }
  }

  @Benchmark public void currentSpan() {
    currentSpan(tracer, extracted.context(), false);
  }

  @Benchmark public void currentSpan_tag() {
    currentSpan(tracer, extracted.context(), true);
  }

  @Benchmark public void currentSpan_unsampled() {
    currentSpan(tracer, unsampledExtracted.context(), false);
  }

  void currentSpan(Tracer tracer, TraceContext context, boolean tag) {
    try (CurrentTraceContext.Scope scope = tracer.currentTraceContext.newScope(context)) {
      Span span = tracer.currentSpan();
      if (tag) span.tag("customer.id", "1234");
    }
  }

  // Convenience main entry-point
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + TracerBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
