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
package brave.grpc;

import brave.grpc.GrpcPropagation.Tags;
import brave.internal.HexCodec;
import brave.internal.PropagationFields;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import io.grpc.Metadata;
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
public class GrpcPropagationBenchmarks {
  static final Propagation<Metadata.Key<String>> b3 =
    B3Propagation.FACTORY.create(AsciiMetadataKeyFactory.INSTANCE);
  static final Injector<Metadata> b3Injector = b3.injector(TracingClientInterceptor.SETTER);
  static final Extractor<Metadata> b3Extractor = b3.extractor(TracingServerInterceptor.GETTER);

  static final Propagation.Factory bothFactory = GrpcPropagation.newFactory(B3Propagation.FACTORY);
  static final Propagation<Metadata.Key<String>> both =
    bothFactory.create(AsciiMetadataKeyFactory.INSTANCE);
  static final Injector<Metadata> bothInjector = both.injector(TracingClientInterceptor.SETTER);
  static final Extractor<Metadata> bothExtractor = both.extractor(TracingServerInterceptor.GETTER);

  static final TraceContext context = TraceContext.newBuilder()
    .traceIdHigh(HexCodec.lowerHexToUnsignedLong("67891233abcdef01"))
    .traceId(HexCodec.lowerHexToUnsignedLong("2345678912345678"))
    .spanId(HexCodec.lowerHexToUnsignedLong("463ac35c9f6413ad"))
    .sampled(true)
    .build();
  static final TraceContext contextWithTags = bothFactory.decorate(context);

  static final Metadata incomingB3 = new Metadata();
  static final Metadata incomingBoth = new Metadata();
  static final Metadata incomingBothNoTags = new Metadata();
  static final Metadata nothingIncoming = new Metadata();

  static {
    PropagationFields.put(contextWithTags, "method", "helloworld.Greeter/SayHello", Tags.class);
    b3Injector.inject(context, incomingB3);
    bothInjector.inject(contextWithTags, incomingBoth);
    bothInjector.inject(context, incomingBothNoTags);
  }

  @Benchmark public void inject_b3() {
    Metadata carrier = new Metadata();
    b3Injector.inject(context, carrier);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_b3() {
    return b3Extractor.extract(incomingBoth);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_b3_nothing() {
    return b3Extractor.extract(nothingIncoming);
  }

  @Benchmark public void inject_both() {
    Metadata carrier = new Metadata();
    bothInjector.inject(contextWithTags, carrier);
  }

  @Benchmark public void inject_both_no_tags() {
    Metadata carrier = new Metadata();
    bothInjector.inject(context, carrier);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_both() {
    return bothExtractor.extract(incomingBoth);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_both_nothing() {
    return bothExtractor.extract(nothingIncoming);
  }

  @Benchmark public TraceContextOrSamplingFlags extract_both_no_tags() {
    return bothExtractor.extract(incomingBothNoTags);
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .addProfiler("gc")
      .include(".*" + GrpcPropagationBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
