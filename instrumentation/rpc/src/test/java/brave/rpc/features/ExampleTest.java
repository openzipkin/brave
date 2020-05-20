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
package brave.rpc.features;

import brave.Tracing;
import brave.rpc.RpcRuleSampler;
import brave.rpc.RpcTracing;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunctions;
import org.junit.Test;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static brave.rpc.RpcRequestMatchers.serviceEquals;
import static org.mockito.Mockito.mock;

public class ExampleTest {
  Tracing tracing = mock(Tracing.class);
  RpcTracing rpcTracing;

  // This mainly shows that we don't accidentally rely on package-private access
  @Test public void showConstruction() {
    rpcTracing = RpcTracing.newBuilder(tracing)
        .serverSampler(RpcRuleSampler.newBuilder()
            .putRule(serviceEquals("scribe"), Sampler.NEVER_SAMPLE)
            .putRule(methodEquals("Report"), RateLimitingSampler.create(100))
            .build())
        .clientSampler(SamplerFunctions.neverSample())
        .build();
  }
}
