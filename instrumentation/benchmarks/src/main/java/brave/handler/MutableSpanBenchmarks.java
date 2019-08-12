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
package brave.handler;

import brave.Span;
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

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Threads(1)
public class MutableSpanBenchmarks {

  @Benchmark public MutableSpan makeServerSpan() {
    return newServerMutableSpan();
  }

  public static MutableSpan newServerMutableSpan() {
    MutableSpan span = new MutableSpan();
    span.name("get /");
    span.kind(Span.Kind.SERVER);
    span.remoteIpAndPort("::1", 63596);
    span.startTimestamp(1533706251750057L);
    span.finishTimestamp(1533706251935296L);
    span.tag("http.method", "GET");
    span.tag("http.path", "/");
    span.tag("mvc.controller.class", "Frontend");
    span.tag("mvc.controller.method", "callBackend");
    return span;
  }

  @Benchmark public MutableSpan makeBigClientSpan() {
    return newBigClientMutableSpan();
  }

  public static MutableSpan newBigClientMutableSpan() {
    MutableSpan span = new MutableSpan();
    span.name("getuserinfobyaccesstoken");
    span.kind(Span.Kind.CLIENT);
    span.remoteServiceName("abasdasgad.hsadas.ism");
    span.remoteIpAndPort("219.235.216.11", 0);
    span.startTimestamp(1533706251750057L);
    span.finishTimestamp(1533706251935296L);
    span.tag("address.local", "/10.1.2.3:59618");
    span.tag("address.remote", "abasdasgad.hsadas.ism/219.235.216.11:8080");
    span.tag("http.host", "abasdasgad.hsadas.ism");
    span.tag("http.method", "POST");
    span.tag("http.path", "/thrift/shopForTalk");
    span.tag("http.status_code", "200");
    span.tag("http.url", "tbinary+h2c://abasdasgad.hsadas.ism/thrift/shopForTalk");
    span.tag("instanceId", "line-wallet-api");
    span.tag("phase", "beta");
    span.tag("siteId", "shop");
    return span;
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + MutableSpanBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
