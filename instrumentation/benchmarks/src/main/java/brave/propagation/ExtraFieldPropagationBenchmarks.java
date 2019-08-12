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
package brave.propagation;

import brave.internal.HexCodec;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ExtraFieldPropagationBenchmarks {
  static final Propagation.Factory
    factory = ExtraFieldPropagation.newFactory(B3Propagation.FACTORY, "x-vcap-request-id");
  static final Propagation<String> extra = factory.create(Propagation.KeyFactory.STRING);
  static final Injector<Map<String, String>> extraInjector = extra.injector(Map::put);
  static final Extractor<Map<String, String>> extraExtractor = extra.extractor(Map::get);

  static final TraceContext context = TraceContext.newBuilder()
    .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef01"))
    .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
    .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
    .sampled(true)
    .build();

  static final Map<String, String> incoming = new LinkedHashMap<String, String>() {
    {
      extraInjector.inject(context, this);
      put("x-vcap-request-id", "216a2aea45d08fc9");
    }
  };

  static final Map<String, String> incomingNoExtra = new LinkedHashMap<String, String>() {
    {
      extraInjector.inject(context, this);
    }
  };

  static final Map<String, String> nothingIncoming = Collections.emptyMap();

  @Benchmark public void inject() {
    Map<String, String> carrier = new LinkedHashMap<>();
    extraInjector.inject(context, carrier);
  }

  @Benchmark public TraceContextOrSamplingFlags extract() {
    return extraExtractor.extract(incoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_nothing() {
    return extraExtractor.extract(nothingIncoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_no_extra() {
    return extraExtractor.extract(incomingNoExtra);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + ExtraFieldPropagationBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
