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
package brave.rpc;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import org.junit.Test;

import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class RpcTracingTest {
  Tracing tracing = mock(Tracing.class);

  @Test public void defaultSamplersDefer() {
    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing).build();

    assertThat(rpcTracing.clientSampler())
        .isSameAs(deferDecision());
    assertThat(rpcTracing.serverSampler())
        .isSameAs(deferDecision());
  }

  @Test public void toBuilder() {
    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing).build();

    assertThat(rpcTracing.toBuilder().build())
        .usingRecursiveComparison()
        .isEqualTo(rpcTracing);

    assertThat(rpcTracing.toBuilder().clientSampler(neverSample()).build())
        .usingRecursiveComparison()
        .isEqualTo(RpcTracing.newBuilder(tracing).clientSampler(neverSample()).build());
  }

  @Test public void canOverridePropagation() {
    Propagation<String> propagation = B3Propagation.newFactoryBuilder()
      .injectFormat(B3Propagation.Format.SINGLE)
      .build().get();

    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing)
      .propagation(propagation)
      .build();

    assertThat(rpcTracing.propagation())
      .isSameAs(propagation);

    rpcTracing = RpcTracing.create(tracing).toBuilder()
      .propagation(propagation)
      .build();

    assertThat(rpcTracing.propagation())
      .isSameAs(propagation);
  }
}
