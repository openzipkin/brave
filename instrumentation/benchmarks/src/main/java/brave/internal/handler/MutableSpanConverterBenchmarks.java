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
package brave.internal.handler;

import brave.ErrorParser;
import brave.handler.MutableSpan;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.Span;

import static brave.handler.MutableSpanBenchmarks.newBigClientMutableSpan;
import static brave.handler.MutableSpanBenchmarks.newServerMutableSpan;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class MutableSpanConverterBenchmarks {
  final MutableSpanConverter converter =
    new MutableSpanConverter(new ErrorParser(), "unknown", "127.0.0.1", 0);
  final MutableSpan serverMutableSpan = newServerMutableSpan();
  final MutableSpan bigClientMutableSpan = newBigClientMutableSpan();

  /**
   * Tests converting into a builder type. This isolates the performance of walking over the mutable
   * data.
   */
  @Benchmark public Span.Builder convertServerSpan() {
    Span.Builder builder = Span.newBuilder();
    converter.convert(serverMutableSpan, builder);
    return builder;
  }

  @Benchmark public Span.Builder convertBigClientSpan() {
    Span.Builder builder = Span.newBuilder();
    converter.convert(bigClientMutableSpan, builder);
    return builder;
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + MutableSpanConverterBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
