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
package brave.propagation;

import brave.baggage.BaggageFields;
import brave.baggage.BaggagePropagation;
import brave.context.log4j2.ThreadContextScopeDecorator;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import static brave.baggage.BaggagePropagationBenchmarks.BAGGAGE_FIELD;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class CurrentTraceContextBenchmarks {
  static final CurrentTraceContext base = ThreadLocalCurrentTraceContext.create();
  static final CurrentTraceContext log4j2OnlyTraceId = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(ThreadContextScopeDecorator.newBuilder()
      .clearFields()
      .addField(BaggageFields.TRACE_ID)
      .build())
    .build();
  static final CurrentTraceContext log4j2OnlyBaggage = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(ThreadContextScopeDecorator.newBuilder()
      .clearFields()
      .addField(BAGGAGE_FIELD)
      .build())
    .build();
  static final CurrentTraceContext log4j2 = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(ThreadContextScopeDecorator.get())
    .build();

  static final Propagation.Factory baggageFactory =
    BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY).addRemoteField(BAGGAGE_FIELD).build();

  static final CurrentTraceContext log4j2Baggage = ThreadLocalCurrentTraceContext.newBuilder()
    .addScopeDecorator(ThreadContextScopeDecorator.newBuilder().addField(BAGGAGE_FIELD).build())
    .build();

  static final TraceContext context = baggageFactory.decorate(TraceContext.newBuilder()
    .traceId(1L)
    .parentId(2L)
    .spanId(3L)
    .sampled(true)
    .build());

  static {
    BAGGAGE_FIELD.updateValue(context, "romeo");
  }

  final CurrentTraceContext.Scope log4j2Scope = log4j2.newScope(context);

  @TearDown public void closeScope() {
    log4j2Scope.close();
  }

  @Benchmark public void newScope_default() {
    try (CurrentTraceContext.Scope ws = base.newScope(context)) {
    }
  }

  @Benchmark public void newScope_log4j2() {
    try (CurrentTraceContext.Scope ws = log4j2.newScope(context)) {
    }
  }

  @Benchmark public void newScope_log4j2_onlyTraceId() {
    try (CurrentTraceContext.Scope ws = log4j2OnlyTraceId.newScope(context)) {
    }
  }

  @Benchmark public void newScope_log4j2_onlyBaggage() {
    try (CurrentTraceContext.Scope ws = log4j2OnlyBaggage.newScope(context)) {
    }
  }

  @Benchmark public void newScope_log4j2_baggage() {
    try (CurrentTraceContext.Scope ws = log4j2Baggage.newScope(context)) {
    }
  }

  @Benchmark public void newScope_redundant_default() {
    try (CurrentTraceContext.Scope ws = base.newScope(context)) {
    }
  }

  @Benchmark public void newScope_redundant_log4j2() {
    try (CurrentTraceContext.Scope ws = log4j2.newScope(context)) {
    }
  }

  @Benchmark public void newScope_clear_default() {
    try (CurrentTraceContext.Scope ws = base.newScope(null)) {
    }
  }

  @Benchmark public void newScope_clear_log4j2() {
    try (CurrentTraceContext.Scope ws = log4j2.newScope(null)) {
    }
  }

  @Benchmark public void maybeScope_default() {
    try (CurrentTraceContext.Scope ws = base.maybeScope(context)) {
    }
  }

  @Benchmark public void maybeScope_log4j2() {
    try (CurrentTraceContext.Scope ws = log4j2.maybeScope(context)) {
    }
  }

  @Benchmark public void maybeScope_redundant_default() {
    try (CurrentTraceContext.Scope ws = base.maybeScope(context)) {
    }
  }

  @Benchmark public void maybeScope_redundant_log4j2() {
    try (CurrentTraceContext.Scope ws = log4j2.maybeScope(context)) {
    }
  }

  @Benchmark public void maybeScope_clear_default() {
    try (CurrentTraceContext.Scope ws = base.maybeScope(null)) {
    }
  }

  @Benchmark public void maybeScope_clear_log4j2() {
    try (CurrentTraceContext.Scope ws = log4j2.maybeScope(null)) {
    }
  }

  // Convenience main entry-point
  public static void main(String[] args) throws Exception {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + CurrentTraceContextBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
