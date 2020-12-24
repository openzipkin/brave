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
package brave.internal.codec;

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

import static brave.internal.codec.CharSequencesBenchmarks.TRACESTATE_B3_MIDDLE;
import static brave.internal.codec.CharSequencesBenchmarks.TRACESTATE_B3_MIDDLE_MAX_ENTRIES;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class EntrySplitterBenchmarks {
  // covers pattern used in https://github.com/openzipkin-contrib/brave-propagation-w3c
  static final EntrySplitter ENTRY_SPLITTER = EntrySplitter.newBuilder()
    .maxEntries(32) // https://www.w3.org/TR/trace-context/#list
    .entrySeparator(',')
    .trimOWSAroundEntrySeparator(true) // https://www.w3.org/TR/trace-context/#list
    .keyValueSeparator('=')
    .trimOWSAroundKeyValueSeparator(false) // https://github.com/w3c/trace-context/pull/411
    .shouldThrow(false)
    .build();

  static final EntrySplitter.Handler<Boolean> NOOP_HANDLER =
    (target, input, beginKey, endKey, beginValue, endValue) -> true;

  @Benchmark public boolean parse_normal() {
    return ENTRY_SPLITTER.parse(NOOP_HANDLER, false, TRACESTATE_B3_MIDDLE);
  }

  @Benchmark public boolean parse_normal_string() {
    return parse_string(TRACESTATE_B3_MIDDLE);
  }

  @Benchmark public boolean parse_max() {
    return ENTRY_SPLITTER.parse(NOOP_HANDLER, false, TRACESTATE_B3_MIDDLE_MAX_ENTRIES);
  }

  @Benchmark public boolean parse_max_string() {
    return parse_string(TRACESTATE_B3_MIDDLE_MAX_ENTRIES);
  }

  static boolean parse_string(String input) {
    boolean result = true;
    for (String entry : input.split(",", 32)) {
      result = entry.trim().split("=", 2).length == 2;
    }
    return result;
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + EntrySplitterBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
