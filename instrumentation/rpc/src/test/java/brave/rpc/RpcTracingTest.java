/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.Tracing;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import org.junit.jupiter.api.Test;

import static brave.sampler.SamplerFunctions.deferDecision;
import static brave.sampler.SamplerFunctions.neverSample;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class RpcTracingTest {
  Tracing tracing = mock(Tracing.class);

  @Test void defaultSamplersDefer() {
    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing).build();

    assertThat(rpcTracing.clientSampler())
        .isSameAs(deferDecision());
    assertThat(rpcTracing.serverSampler())
        .isSameAs(deferDecision());
  }

  @Test void toBuilder() {
    RpcTracing rpcTracing = RpcTracing.newBuilder(tracing).build();

    assertThat(rpcTracing.toBuilder().build())
        .usingRecursiveComparison()
        .isEqualTo(rpcTracing);

    assertThat(rpcTracing.toBuilder().clientSampler(neverSample()).build())
        .usingRecursiveComparison()
        .isEqualTo(RpcTracing.newBuilder(tracing).clientSampler(neverSample()).build());
  }

  @Test void canOverridePropagation() {
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
