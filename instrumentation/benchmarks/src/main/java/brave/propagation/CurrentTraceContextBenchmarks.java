/**
 * Copyright 2015-2016 The OpenZipkin Authors
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

import brave.context.slf4j.MDCCurrentTraceContext;
import brave.internal.HexCodec;
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
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class CurrentTraceContextBenchmarks {
  static final CurrentTraceContext base = CurrentTraceContext.Default.create();
  static final CurrentTraceContext slf4j = MDCCurrentTraceContext.create(base);

  static final TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef012345678912345678"))
      .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
      .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
      .build();

  static final TraceContext contextWithParent = context.toBuilder()
      .parentId(context.spanId())
      .spanId(HexCodec.lowerHexToUnsignedLong("e64ac35c9f641ea3"))
      .build();

  final CurrentTraceContext.Scope slf4jScope = slf4j.newScope(context);

  @TearDown public void closeScope() {
    slf4jScope.close();
  }

  @Benchmark public void newScope_default() {
    try (CurrentTraceContext.Scope ws = base.newScope(contextWithParent)) {
    }
  }

  @Benchmark public void newScope_slf4j() {
    try (CurrentTraceContext.Scope ws = slf4j.newScope(contextWithParent)) {
    }
  }

  @Benchmark public void newScope_redundant_default() {
    try (CurrentTraceContext.Scope ws = base.newScope(context)) {
    }
  }

  @Benchmark public void newScope_redundant_slf4j() {
    try (CurrentTraceContext.Scope ws = slf4j.newScope(context)) {
    }
  }

  @Benchmark public void newScope_clear_default() {
    try (CurrentTraceContext.Scope ws = base.newScope(null)) {
    }
  }

  @Benchmark public void newScope_clear_slf4j() {
    try (CurrentTraceContext.Scope ws = slf4j.newScope(null)) {
    }
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(".*" + CurrentTraceContextBenchmarks.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }
}
