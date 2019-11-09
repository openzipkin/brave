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
package brave.http;

import brave.Tracing;
import org.junit.Test;

import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class HttpTracingTest {
  Tracing tracing = mock(Tracing.class);

  @Test public void defaultSamplersDefer() {
    HttpTracing rpcTracing = HttpTracing.newBuilder(tracing).build();

    assertThat(rpcTracing.clientRequestSampler())
      .isSameAs(deferDecision());
    assertThat(rpcTracing.serverRequestSampler())
      .isSameAs(deferDecision());
  }

  @Test public void toBuilder() {
    HttpTracing rpcTracing = HttpTracing.newBuilder(tracing).build();

    assertThat(rpcTracing.toBuilder().build())
      .usingRecursiveComparison()
      .isEqualTo(rpcTracing);

    assertThat(rpcTracing.toBuilder().clientSampler(neverSample()).build())
      .usingRecursiveComparison()
      .isEqualTo(HttpTracing.newBuilder(tracing).clientSampler(neverSample()).build());
  }
}
