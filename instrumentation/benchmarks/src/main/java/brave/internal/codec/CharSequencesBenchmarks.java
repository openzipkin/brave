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

import static brave.internal.codec.CharSequences.regionMatches;
import static brave.internal.codec.CharSequences.withoutSubSequence;

/**
 * For {@link CharSequences#withoutSubSequence(CharSequence, int, int)}, this only benchmarks the
 * interesting case, which is when the substring to exclude is in the middle of the input. This
 * shows how it helps reduce GC pressure vs concatenating strings.
 *
 * <p>The use case for this is we are writing down a "tracestate" header, and overwriting only the
 * "b3" entry of it. We write this header using {@link StringBuilder#append(CharSequence)}, which
 * implies the input does not necessarily need to be a String. This flexibility allows us to avoid
 * the allocation implied when otherwise concatenating ranges to exclude a middle part of a string.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CharSequencesBenchmarks {
  static final String B3_128 = "67891233abcdef012345678912345678-463ac35c9f6413ad-1";
  static final String TRACESTATE_B3_ONLY = "b3=" + B3_128;
  static final String TRACESTATE_B3_MIDDLE =
    "other=28167db2-2de7-4c63-ae3f-f56e7a0eca65,b3=" + B3_128 + ",another=               a";
  static final String TRACESTATE_B3_MIDDLE_MAX_ENTRIES;

  static {
    StringBuilder builder = new StringBuilder().append(0).append('=').append(B3_128);
    for (int i = 1; i < 32; i++) {
      builder.append(',').append(i == 15 ? "b3" : i).append('=').append(B3_128);
    }
    TRACESTATE_B3_MIDDLE_MAX_ENTRIES = builder.toString();
  }

  static final int INDEX_OF_TRACESTATE_B3_MIDDLE = TRACESTATE_B3_MIDDLE.indexOf(TRACESTATE_B3_ONLY);
  static final int INDEX_OF_TRACESTATE_B3_MIDDLE_MAX_ENTRIES =
    TRACESTATE_B3_MIDDLE_MAX_ENTRIES.indexOf(TRACESTATE_B3_ONLY);

  @Benchmark public boolean regionMatches_hit() {
    return regionMatches(TRACESTATE_B3_ONLY, TRACESTATE_B3_MIDDLE, INDEX_OF_TRACESTATE_B3_MIDDLE,
      INDEX_OF_TRACESTATE_B3_MIDDLE + TRACESTATE_B3_ONLY.length());
  }

  @Benchmark public boolean regionMatches_miss() {
    return regionMatches(TRACESTATE_B3_ONLY, TRACESTATE_B3_MIDDLE, 0, TRACESTATE_B3_ONLY.length());
  }

  @Benchmark public CharSequence withoutSubSequence_middle_normal() {
    return withoutSubSequence(TRACESTATE_B3_MIDDLE, INDEX_OF_TRACESTATE_B3_MIDDLE,
      INDEX_OF_TRACESTATE_B3_MIDDLE + TRACESTATE_B3_ONLY.length());
  }

  @Benchmark public CharSequence withoutSubSequence_middle_normal_string() {
    return withoutSubSequence_string(TRACESTATE_B3_MIDDLE, INDEX_OF_TRACESTATE_B3_MIDDLE,
      INDEX_OF_TRACESTATE_B3_MIDDLE + TRACESTATE_B3_ONLY.length());
  }

  @Benchmark public CharSequence withoutSubSequence_middle_max() {
    return withoutSubSequence(TRACESTATE_B3_MIDDLE_MAX_ENTRIES,
      INDEX_OF_TRACESTATE_B3_MIDDLE_MAX_ENTRIES,
      INDEX_OF_TRACESTATE_B3_MIDDLE_MAX_ENTRIES + TRACESTATE_B3_ONLY.length());
  }

  @Benchmark public CharSequence withoutSubSequence_middle_max_string() {
    return withoutSubSequence_string(TRACESTATE_B3_MIDDLE_MAX_ENTRIES,
      INDEX_OF_TRACESTATE_B3_MIDDLE_MAX_ENTRIES,
      INDEX_OF_TRACESTATE_B3_MIDDLE_MAX_ENTRIES + TRACESTATE_B3_ONLY.length());
  }

  static CharSequence withoutSubSequence_string(String input, int beginIndex, int endIndex) {
    // String.subSequence is the same as String.substring!
    return input.substring(0, beginIndex) + input.substring(endIndex);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + CharSequencesBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
