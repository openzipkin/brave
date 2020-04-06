/*
 * Copyright 2013-2020 The OpenZipkin Authors
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

import brave.baggage.BaggagePropagation;
import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.propagation.B3Propagation;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation;
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

import static brave.baggage.BaggagePropagationBenchmarks.BAGGAGE_FIELD;
import static brave.propagation.SamplingFlags.NOT_SAMPLED;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Threads(2)
@State(Scope.Benchmark)
public class TracerBenchmarks {
  Propagation.Factory baggageFactory = BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
    .add(SingleBaggageField.remote(BAGGAGE_FIELD)).build();

  TraceContext context =
    TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).sampled(true).build();
  TraceContext contextBaggage = baggageFactory.decorate(context);
  TraceContext unsampledContext =
    TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).sampled(false).build();
  TraceContext unsampledContextBaggage = baggageFactory.decorate(unsampledContext);
  TraceContext sampledLocalContext = unsampledContext.toBuilder().sampledLocal(true).build();
  TraceContext sampledLocalContextBaggage = baggageFactory.decorate(sampledLocalContext);
  TraceContextOrSamplingFlags extracted = TraceContextOrSamplingFlags.create(context);
  TraceContextOrSamplingFlags extractedBaggage = TraceContextOrSamplingFlags.create(contextBaggage);
  TraceContextOrSamplingFlags unsampledExtracted = TraceContextOrSamplingFlags.create(NOT_SAMPLED);
  TraceContextOrSamplingFlags unsampledExtractedBaggage =
    TraceContextOrSamplingFlags.newBuilder()
      .samplingFlags(NOT_SAMPLED)
      .addExtra(contextBaggage.extra())
      .build();

  Tracer tracer;
  Tracer tracerBaggage;

  @Setup(Level.Trial) public void init() {
    tracer = Tracing.newBuilder()
      .addFinishedSpanHandler(new FinishedSpanHandler() {
        @Override public boolean handle(TraceContext context, MutableSpan span) {
          return true; // anonymous subtype prevents all recording from being no-op
        }
      })
      .spanReporter(Reporter.NOOP).build().tracer();
    tracerBaggage = Tracing.newBuilder().propagationFactory(baggageFactory)
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

  @Benchmark public void startScopedSpanWithParent_baggage() {
    startScopedSpanWithParent(tracerBaggage, contextBaggage);
  }

  @Benchmark public void startScopedSpanWithParent_unsampled() {
    startScopedSpanWithParent(tracer, unsampledContext);
  }

  @Benchmark public void startScopedSpanWithParent_unsampled_baggage() {
    startScopedSpanWithParent(tracerBaggage, unsampledContextBaggage);
  }

  @Benchmark public void startScopedSpanWithParent_sampledLocal() {
    startScopedSpanWithParent(tracer, sampledLocalContext);
  }

  @Benchmark public void startScopedSpanWithParent_sampledLocal_baggage() {
    startScopedSpanWithParent(tracerBaggage, sampledLocalContextBaggage);
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

  @Benchmark public void newChildWithSpanInScope_baggage() {
    newChildWithSpanInScope(tracerBaggage, contextBaggage);
  }

  @Benchmark public void newChildWithSpanInScope_unsampled() {
    newChildWithSpanInScope(tracer, unsampledContext);
  }

  @Benchmark public void newChildWithSpanInScope_unsampled_baggage() {
    newChildWithSpanInScope(tracerBaggage, unsampledContextBaggage);
  }

  @Benchmark public void newChildWithSpanInScope_sampledLocal() {
    newChildWithSpanInScope(tracer, sampledLocalContext);
  }

  @Benchmark public void newChildWithSpanInScope_sampledLocal_baggage() {
    newChildWithSpanInScope(tracerBaggage, sampledLocalContextBaggage);
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

  @Benchmark public void joinWithSpanInScope_baggage() {
    joinWithSpanInScope(tracerBaggage, contextBaggage);
  }

  @Benchmark public void joinWithSpanInScope_unsampled() {
    joinWithSpanInScope(tracer, unsampledContext);
  }

  @Benchmark public void joinWithSpanInScope_unsampled_baggage() {
    joinWithSpanInScope(tracerBaggage, unsampledContextBaggage);
  }

  @Benchmark public void joinWithSpanInScope_sampledLocal() {
    joinWithSpanInScope(tracer, sampledLocalContext);
  }

  @Benchmark public void joinWithSpanInScope_sampledLocal_baggage() {
    joinWithSpanInScope(tracerBaggage, sampledLocalContextBaggage);
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

  @Benchmark public void nextWithSpanInScope_baggage() {
    nextWithSpanInScope(tracerBaggage, extractedBaggage);
  }

  @Benchmark public void nextWithSpanInScope_unsampled() {
    nextWithSpanInScope(tracer, unsampledExtracted);
  }

  @Benchmark public void nextWithSpanInScope_unsampled_baggage() {
    nextWithSpanInScope(tracerBaggage, unsampledExtractedBaggage);
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
