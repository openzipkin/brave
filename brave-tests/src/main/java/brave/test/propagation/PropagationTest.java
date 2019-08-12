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
package brave.test.propagation;

import brave.internal.HexCodec;
import brave.internal.Nullable;
import brave.propagation.Propagation;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.test.util.ClassLoaders;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadableWithSupplier;
import static brave.test.util.ClassLoaders.newInstance;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class PropagationTest<K> {

  protected abstract Class<? extends Supplier<Propagation<K>>> propagationSupplier();

  protected abstract void inject(Map<K, String> map, @Nullable String traceId,
    @Nullable String parentId, @Nullable String spanId, @Nullable Boolean sampled,
    @Nullable Boolean debug);

  /**
   * There's currently no standard API to just inject sampling flags, as IDs are intended to be
   * propagated.
   */
  protected abstract void inject(Map<K, String> carrier, SamplingFlags samplingFlags);

  protected Map<K, String> map = new LinkedHashMap<>();
  MapEntry<K> mapEntry = new MapEntry<>();

  TraceContext rootSpan = TraceContext.newBuilder()
    .traceId(1L)
    .spanId(1L)
    .sampled(true).build();
  TraceContext childSpan = rootSpan.toBuilder()
    .parentId(rootSpan.spanId())
    .spanId(2).build();

  protected final Propagation<K> propagation;

  protected PropagationTest() {
    propagation = newInstance(propagationSupplier(), getClass().getClassLoader()).get();
  }

  @Test public void verifyRoundTrip_rootSpan() throws Exception {
    inject(map, "0000000000000001", null, "0000000000000001", true, null);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(rootSpan));
  }

  @Test public void verifyRoundTrip_128BitTrace() throws Exception {
    String high64Bits = "463ac35c9f6413ad";
    String low64Bits = "48485a3953bb6124";
    inject(map, high64Bits + low64Bits, null, low64Bits, true, null);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(rootSpan.toBuilder()
      .traceIdHigh(HexCodec.lowerHexToUnsignedLong(high64Bits))
      .traceId(HexCodec.lowerHexToUnsignedLong(low64Bits))
      .spanId(HexCodec.lowerHexToUnsignedLong(low64Bits)).build()));
  }

  @Test public void verifyRoundTrip_childSpan() throws Exception {
    inject(map, "0000000000000001", "0000000000000001", "0000000000000002", true, null);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(childSpan));
  }

  @Test public void verifyRoundTrip_notSampled() throws Exception {
    inject(map, "0000000000000001", "0000000000000001", "0000000000000002", false, null);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(
      childSpan.toBuilder().sampled(false).build()
    ));
  }

  @Test public void verifyRoundTrip_notSampled_noIds() throws Exception {
    inject(map, null, null, null, false, null);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(SamplingFlags.NOT_SAMPLED));
  }

  @Test public void verifyRoundTrip_sampledTrueNoOtherTraceHeaders() {
    inject(map, null, null, null, true, null);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(SamplingFlags.SAMPLED));
  }

  @Test public void verifyRoundTrip_debug() {
    inject(map, null, null, null, null, true);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(SamplingFlags.DEBUG));
  }

  @Test public void verifyRoundTrip_empty() throws Exception {
    inject(map, null, null, null, null, null);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(SamplingFlags.EMPTY));
  }

  /**
   * When the caller propagates IDs, but not a sampling decision, the current process should
   * decide.
   */
  @Test public void verifyRoundTrip_externallyProvidedIds() {
    inject(map, "0000000000000001", null, "0000000000000001", null, null);

    verifyRoundTrip(TraceContextOrSamplingFlags.create(rootSpan.toBuilder().sampled(null).build()));
  }

  void verifyRoundTrip(TraceContextOrSamplingFlags expected) {
    TraceContextOrSamplingFlags extracted = propagation.extractor(mapEntry).extract(map);

    assertThat(extracted)
      .isEqualTo(expected);

    Map<K, String> injected = new LinkedHashMap<>();
    if (expected.context() != null) {
      propagation.injector(mapEntry).inject(expected.context(), injected);
    } else {
      inject(injected, expected.samplingFlags());
    }

    assertThat(map).isEqualTo(injected);
  }

  protected static class MapEntry<K> implements
    Propagation.Getter<Map<K, String>, K>,
    Propagation.Setter<Map<K, String>, K> {
    public MapEntry() {
    }

    @Override public void put(Map<K, String> carrier, K key, String value) {
      carrier.put(key, value);
    }

    @Override public String get(Map<K, String> carrier, K key) {
      return carrier.get(key);
    }
  }

  @Test public void unloadable_unused() {
    assertRunIsUnloadableWithSupplier(Unused.class, propagationSupplier());
  }

  static class Unused extends ClassLoaders.ConsumerRunnable<Propagation<?>> {
    @Override public void accept(Propagation<?> propagation) {
    }
  }

  @Test public void unloadable_afterBasicUsage() {
    assertRunIsUnloadableWithSupplier(BasicUsage.class, propagationSupplier());
  }

  static class BasicUsage extends ClassLoaders.ConsumerRunnable<Propagation<?>> {
    @Override public void accept(Propagation<?> propagation) {
      TraceContext.Injector<Map<Object, String>> injector = propagation.injector(Map::put);
      TraceContext.Extractor<Map<Object, String>> extractor = propagation.extractor(Map::get);

      TraceContext ctx = TraceContext.newBuilder().traceId(1L).spanId(2L).sampled(false).build();
      Map<Object, String> map = new LinkedHashMap<>();
      injector.inject(ctx, map);

      assertThat(extractor.extract(map).context())
        .isEqualToIgnoringGivenFields(ctx, "traceIdString", "spanIdString");
    }
  }
}
