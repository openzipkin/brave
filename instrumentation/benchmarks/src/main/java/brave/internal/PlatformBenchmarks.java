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
package brave.internal;

import brave.Clock;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
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
public class PlatformBenchmarks {
  static final Platform jre6 = new Platform.Jre6();
  static final Platform jre7 = new Platform.Jre7();
  static final Platform jre9 = new Platform.Jre9();
  static final Clock jre7Clock = jre7.clock();
  static final Clock jre9Clock = jre9.clock();

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public long no_contention_nextTraceIdHigh_jre6() {
    return jre6.nextTraceIdHigh();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public long mild_contention_nextTraceIdHigh_jre6() {
    return jre6.nextTraceIdHigh();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public long high_contention_nextTraceIdHigh_jre6() {
    return jre6.nextTraceIdHigh();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public long no_contention_randomLong_jre6() {
    return jre6.randomLong();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public long mild_contention_randomLong_jre6() {
    return jre6.randomLong();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public long high_contention_randomLong_jre6() {
    return jre6.randomLong();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public long no_contention_nextTraceIdHigh_jre7() {
    return jre7.nextTraceIdHigh();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public long mild_contention_nextTraceIdHigh_jre7() {
    return jre7.nextTraceIdHigh();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public long high_contention_nextTraceIdHigh_jre7() {
    return jre7.nextTraceIdHigh();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public long no_contention_randomLong_jre7() {
    return jre7.randomLong();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public long mild_contention_randomLong_jre7() {
    return jre7.randomLong();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public long high_contention_randomLong_jre7() {
    return jre7.randomLong();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public long no_contention_clock_jre7() {
    return jre7Clock.currentTimeMicroseconds();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public long mild_contention_clock_jre7() {
    return jre7Clock.currentTimeMicroseconds();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public long high_contention_clock_jre7() {
    return jre7Clock.currentTimeMicroseconds();
  }

  @Benchmark @Group("no_contention") @GroupThreads(1)
  public long no_contention_clock_jre9() {
    return jre9Clock.currentTimeMicroseconds();
  }

  @Benchmark @Group("mild_contention") @GroupThreads(2)
  public long mild_contention_clock_jre9() {
    return jre9Clock.currentTimeMicroseconds();
  }

  @Benchmark @Group("high_contention") @GroupThreads(8)
  public long high_contention_clock_jre9() {
    return jre9Clock.currentTimeMicroseconds();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + PlatformBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
