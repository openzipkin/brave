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

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static brave.internal.HexCodec.lowerHexToUnsignedLong;
import static brave.propagation.Propagation.KeyFactory.STRING;
import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextPropagationTest {
  Map<String, String> request = new LinkedHashMap<>();
  Propagation<String> propagation = TraceContextPropagation.newFactory().create(STRING);
  Injector<Map<String, String>> injector = propagation.injector(Map::put);
  Extractor<Map<String, String>> extractor = propagation.extractor(Map::get);

  TraceContext sampledContext = TraceContext.newBuilder()
      .traceIdHigh(lowerHexToUnsignedLong("67891233abcdef01"))
      .traceId(lowerHexToUnsignedLong("2345678912345678"))
      .spanId(lowerHexToUnsignedLong("463ac35c9f6413ad"))
      .sampled(true)
      .build();
  String validTraceparent = "00-67891233abcdef012345678912345678-463ac35c9f6413ad-01";
  String validB3Single = "67891233abcdef012345678912345678-463ac35c9f6413ad-1";
  String otherState = "congo=lZWRzIHRoNhcm5hbCBwbGVhc3VyZS4=";

  @Test public void injects_b3_when_no_other_tracestate() {
    sampledContext = sampledContext.toBuilder().addExtra(Tracestate.EMPTY).build();

    injector.inject(sampledContext, request);

    assertThat(request).containsEntry("tracestate", "b3=" + validB3Single);
  }

  @Test public void injects_b3_before_other_tracestate() {
    Tracestate tracestate = Tracestate.create(otherState);

    sampledContext = sampledContext.toBuilder().addExtra(tracestate).build();

    injector.inject(sampledContext, request);

    assertThat(request).containsEntry("tracestate", "b3=" + validB3Single + "," + otherState);
  }

  @Test public void extracts_b3_when_no_other_tracestate() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", "b3=" + validB3Single);

    assertThat(extractor.extract(request)).isEqualTo(
        TraceContextOrSamplingFlags.newBuilder(sampledContext).addExtra(Tracestate.EMPTY).build());
  }

  @Test public void extracts_b3_before_other_tracestate() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", "b3=" + validB3Single + "," + otherState);

    Tracestate tracestate = Tracestate.create(otherState);

    assertThat(extractor.extract(request)).isEqualTo(
        TraceContextOrSamplingFlags.newBuilder(sampledContext).addExtra(tracestate).build());
  }

  @Test public void extracted_toString() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", "b3=" + validB3Single + "," + otherState);

    assertThat(extractor.extract(request)).hasToString(
        "Extracted{"
            + "traceContext=" + sampledContext + ", "
            + "samplingFlags=SAMPLED_REMOTE, "
            + "extra=[tracestate: " + otherState + "]"
            + "}");
  }

  @Test public void extracts_b3_after_other_tracestate() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", otherState + ",b3=" + validB3Single);

    Tracestate tracestate = Tracestate.create(otherState);

    assertThat(extractor.extract(request)).isEqualTo(
        TraceContextOrSamplingFlags.newBuilder(sampledContext).addExtra(tracestate).build());
  }

  @Test public void tracestate() {
    Tracestate b3_withContext = Tracestate.create("b3=" + validB3Single);
    Tracestate sameState = Tracestate.create("b3=" + validB3Single);
    assertThat(b3_withContext).isEqualTo(sameState);
    assertThat(sameState).isEqualTo(b3_withContext);
    assertThat(b3_withContext).hasSameHashCodeAs(sameState);
    assertThat(b3_withContext)
        .hasToString("tracestate: b3=67891233abcdef012345678912345678-463ac35c9f6413ad-1");

    assertThat(Tracestate.create(""))
        .isNotEqualTo(b3_withContext)
        .isSameAs(Tracestate.create(null))
        .isSameAs(Tracestate.EMPTY)
        .hasToString("tracestate: ");

    Tracestate b3_debugOnly = Tracestate.create("b3=d");
    assertThat(b3_withContext).isNotEqualTo(b3_debugOnly);
    assertThat(b3_debugOnly).isNotEqualTo(b3_withContext);
    assertThat(b3_withContext.hashCode()).isNotEqualTo(b3_debugOnly.hashCode());
  }
}
