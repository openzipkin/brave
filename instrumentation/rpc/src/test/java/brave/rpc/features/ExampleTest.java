/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc.features;

import brave.Tracing;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunctions;
import org.junit.jupiter.api.Test;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static org.mockito.Mockito.mock;

public class ExampleTest {
  Tracing tracing = mock(Tracing.class);
  RpcTracing rpcTracing;

  // This mainly shows that we don't accidentally rely on package-private access
  @Test void showConstruction() {
    rpcTracing = RpcTracing.newBuilder(tracing)
        .serverSampler(RpcRuleSampler.newBuilder()
            .putRule(serviceEquals("scribe"), Sampler.NEVER_SAMPLE)
            .putRule(methodEquals("Report"), RateLimitingSampler.create(100))
            .build())
        .clientSampler(SamplerFunctions.neverSample())
        .build();
  }
}
