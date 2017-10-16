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
package brave.internal;

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.propagation.aws.AWSPropagation;
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
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
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
public class PropagationBenchmarks {

  static final Injector<Map<String, String>> b3Injector =
      Propagation.B3_STRING.injector(Map::put);
  static final Injector<Map<String, String>> awsInjector =
      AWSPropagation.FACTORY.create(Propagation.KeyFactory.STRING).injector(Map::put);
  static final Extractor<Map<String, String>> b3Extractor =
      Propagation.B3_STRING.extractor(Map::get);
  static final Extractor<Map<String, String>> awsExtractor =
      AWSPropagation.FACTORY.create(Propagation.KeyFactory.STRING).extractor(Map::get);

  static final TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef01"))
      .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
      .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
      .sampled(true)
      .build();

  static final Map<String, String> incoming = new LinkedHashMap<String, String>() {
    {
      b3Injector.inject(context, this);
      awsInjector.inject(context, this);
    }
  };

  static final Map<String, String> nothingIncoming = Collections.emptyMap();

  Map<String, String> carrier = new LinkedHashMap<>();

  @Benchmark public void inject_b3() {
    b3Injector.inject(context, carrier);
  }

  @Benchmark public void inject_aws() {
    awsInjector.inject(context, carrier);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_b3() {
    return b3Extractor.extract(incoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_aws() {
    return awsExtractor.extract(incoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_b3_nothing() {
    return b3Extractor.extract(nothingIncoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_aws_nothing() {
    return awsExtractor.extract(nothingIncoming);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    new PropagationBenchmarks().extract_aws();
    Options opt = new OptionsBuilder()
        .include(".*" + PropagationBenchmarks.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }
}
