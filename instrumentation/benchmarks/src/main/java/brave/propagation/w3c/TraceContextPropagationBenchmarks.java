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

import brave.internal.HexCodec;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
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
public class TraceContextPropagationBenchmarks {
  static final Propagation<String> tc =
    TraceContextPropagation.newFactory().create(Propagation.KeyFactory.STRING);
  static final Injector<Map<String, String>> tcInjector = tc.injector(Map::put);
  static final Extractor<Map<String, String>> tcExtractor = tc.extractor(Map::get);

  static final TraceContext context = TraceContext.newBuilder()
    .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef01"))
    .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
    .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
    .sampled(true)
    .build();

  // TODO: add tracestate examples which prefer the b3 entry
  static final Map<String, String> incoming = new LinkedHashMap<String, String>() {
    {
      put("traceparent", TraceparentFormat.writeTraceparentFormat(context));
    }
  };

  static final Map<String, String> incomingPadded = new LinkedHashMap<String, String>() {
    {
      put("traceparent",
        TraceparentFormat.writeTraceparentFormat(context.toBuilder().traceIdHigh(0).build()));
    }
  };

  static final Map<String, String> incomingMalformed = new LinkedHashMap<String, String>() {
    {
      put("traceparent", "b970dafd-0d95-40aa-95d8-1d8725aebe40"); // not ok
    }
  };

  static final Map<String, String> nothingIncoming = Collections.emptyMap();

  @Benchmark public void inject() {
    Map<String, String> carrier = new LinkedHashMap<>();
    tcInjector.inject(context, carrier);
  }

  @Benchmark public TraceContextOrSamplingFlags extract() {
    return tcExtractor.extract(incoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_padded() {
    return tcExtractor.extract(incomingPadded);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_nothing() {
    return tcExtractor.extract(nothingIncoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_malformed() {
    return tcExtractor.extract(incomingMalformed);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + TraceContextPropagationBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
