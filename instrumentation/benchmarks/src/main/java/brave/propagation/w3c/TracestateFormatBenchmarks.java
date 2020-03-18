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
package brave.propagation.w3c;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
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

import static brave.propagation.w3c.TracestateFormat.validateKey;

/**
 * This mainly shows the impact of much slower approaches, such as regular expressions. However,
 * this is also used to help us evaluate efficiencies beyond that.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput) // simpler to interpret vs sample time
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class TracestateFormatBenchmarks {
  // see https://github.com/w3c/trace-context/pull/386 for clearer definition of this stuff
  static final String KEY_CHAR = "[a-z0-9_\\-*/]";
  static final Pattern KEY_PATTERN = Pattern.compile("^(" +
    "[a-z]" + KEY_CHAR + "{0,255}" + // Basic Key
    "|" + // OR
    "[a-z0-9]" + KEY_CHAR + "{0,240}@[a-z]" + KEY_CHAR + "{0,13}" + // Tenant Key
    ")$");

  // copied from TracestateFormatTest as we don't share classpath
  static final String FORTY_KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_-*/";
  static final String TWO_HUNDRED_FORTY_KEY_CHARS =
    FORTY_KEY_CHARS + FORTY_KEY_CHARS + FORTY_KEY_CHARS
      + FORTY_KEY_CHARS + FORTY_KEY_CHARS + FORTY_KEY_CHARS;

  static final String LONGEST_BASIC_KEY =
    TWO_HUNDRED_FORTY_KEY_CHARS + FORTY_KEY_CHARS.substring(0, 16);

  static final String LONGEST_TENANT_KEY =
    "1" + TWO_HUNDRED_FORTY_KEY_CHARS + "@" + FORTY_KEY_CHARS.substring(0, 13);

  @Benchmark public boolean validateKey_brave_longest_basic() {
    return validateKey(LONGEST_BASIC_KEY);
  }

  @Benchmark public boolean validateKey_brave_longest_tenant() {
    return validateKey(LONGEST_TENANT_KEY);
  }

  @Benchmark public boolean validateKey_regex_longest_basic() {
    return KEY_PATTERN.matcher(LONGEST_BASIC_KEY).matches();
  }

  @Benchmark public boolean validateKey_regex_longest_tenant() {
    return KEY_PATTERN.matcher(LONGEST_TENANT_KEY).matches();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + TracestateFormatBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
