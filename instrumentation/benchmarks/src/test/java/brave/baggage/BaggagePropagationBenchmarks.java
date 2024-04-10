/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.baggage;

import brave.baggage.BaggagePropagationConfig.SingleBaggageField;
import brave.internal.InternalPropagation;
import brave.internal.codec.HexCodec;
import brave.propagation.B3Propagation;
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
public class BaggagePropagationBenchmarks {
  public static final BaggageField BAGGAGE_FIELD = BaggageField.create("user-id");
  static final Propagation.Factory factory =
    BaggagePropagation.newFactoryBuilder(B3Propagation.FACTORY)
      .add(SingleBaggageField.remote(BAGGAGE_FIELD)).build();
  static final Propagation<String> propagation = factory.get();
  static final Injector<Map<String, String>> injector = propagation.injector(Map::put);
  static final Extractor<Map<String, String>> extractor = propagation.extractor(Map::get);

  static final TraceContext context = TraceContext.newBuilder()
    .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef01"))
    .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
    .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
    .sampled(true)
    .build();

  static final Map<String, String> incoming = new LinkedHashMap<String, String>() {
    {
      injector.inject(context, this);
      put(BAGGAGE_FIELD.name(), "216a2aea45d08fc9");
    }
  };

  static final TraceContext contextWithBaggage = extractor.extract(incoming).context();

  static final Map<String, String> incomingNoBaggage = new LinkedHashMap<String, String>() {
    {
      injector.inject(context, this);
    }
  };

  static final Map<String, String> nothingIncoming = Collections.emptyMap();

  @Benchmark public TraceContext decorate() {
    return factory.decorate(InternalPropagation.instance.shallowCopy(context));
  }

  @Benchmark public TraceContext decorate_withBaggage() {
    return factory.decorate(InternalPropagation.instance.shallowCopy(contextWithBaggage));
  }

  @Benchmark public void inject() {
    Map<String, String> request = new LinkedHashMap<>();
    injector.inject(context, request);
  }

  @Benchmark public TraceContextOrSamplingFlags extract() {
    return extractor.extract(incoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_nothing() {
    return extractor.extract(nothingIncoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_no_baggage() {
    return extractor.extract(incomingNoBaggage);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + BaggagePropagationBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
