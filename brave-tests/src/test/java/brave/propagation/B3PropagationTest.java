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

import brave.internal.Nullable;
import brave.test.propagation.PropagationTest;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class B3PropagationTest extends PropagationTest<String> {

  @Override protected Class<? extends Supplier<Propagation<String>>> propagationSupplier() {
    return PropagationSupplier.class;
  }

  static class PropagationSupplier implements Supplier<Propagation<String>> {
    @Override public Propagation<String> get() {
      return Propagation.B3_STRING;
    }
  }

  @Override protected void inject(Map<String, String> map, @Nullable String traceId,
    @Nullable String parentId, @Nullable String spanId, @Nullable Boolean sampled,
    @Nullable Boolean debug) {
    if (traceId != null) map.put("X-B3-TraceId", traceId);
    if (parentId != null) map.put("X-B3-ParentSpanId", parentId);
    if (spanId != null) map.put("X-B3-SpanId", spanId);
    if (sampled != null) map.put("X-B3-Sampled", sampled ? "1" : "0");
    if (debug != null) map.put("X-B3-Flags", debug ? "1" : "0");
  }

  @Override protected void inject(Map<String, String> carrier, SamplingFlags flags) {
    if (flags.debug()) {
      carrier.put("X-B3-Flags", "1");
    } else if (flags.sampled() != null) {
      carrier.put("X-B3-Sampled", flags.sampled() ? "1" : "0");
    }
  }

  @Test public void extractTraceContext_sampledFalse() {
    MapEntry<String> mapEntry = new MapEntry<>();
    map.put("X-B3-Sampled", "false");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
      .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void extractTraceContext_sampledFalseUpperCase() {
    MapEntry<String> mapEntry = new MapEntry<>();
    map.put("X-B3-Sampled", "FALSE");

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
      .isEqualTo(SamplingFlags.NOT_SAMPLED);
  }

  @Test public void extractTraceContext_malformed() {
    MapEntry<String> mapEntry = new MapEntry<>();
    map.put("X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124"); // ok
    map.put("X-B3-SpanId", "48485a3953bb6124"); // ok
    map.put("X-B3-ParentSpanId", "-"); // not ok

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
      .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extractTraceContext_malformed_sampled() {
    MapEntry<String> mapEntry = new MapEntry<>();
    map.put("X-B3-TraceId", "-"); // not ok
    map.put("X-B3-Sampled", "1"); // ok

    SamplingFlags result = propagation.extractor(mapEntry).extract(map).samplingFlags();

    assertThat(result)
      .isEqualTo(SamplingFlags.EMPTY);
  }

  @Test public void extractTraceContext_debug_with_ids() {
    MapEntry<String> mapEntry = new MapEntry<>();
    map.put("X-B3-TraceId", "463ac35c9f6413ad48485a3953bb6124"); // ok
    map.put("X-B3-SpanId", "48485a3953bb6124"); // ok
    map.put("X-B3-Flags", "1"); // accidentally missing sampled flag

    TraceContext result = propagation.extractor(mapEntry).extract(map).context();

    assertThat(result.sampled())
      .isTrue();
  }

  @Test public void extractTraceContext_singleHeaderFormat() {
    MapEntry<String> mapEntry = new MapEntry<>();

    map.put("b3", "4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7");

    TraceContext result = propagation.extractor(mapEntry).extract(map).context();

    assertThat(result.traceIdString())
      .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(result.spanIdString())
      .isEqualTo("00f067aa0ba902b7");
  }
}
