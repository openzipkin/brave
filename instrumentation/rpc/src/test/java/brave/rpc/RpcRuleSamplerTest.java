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
package brave.rpc;

import brave.sampler.Matcher;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RpcRuleSamplerTest {

  @Mock RpcServerRequest request;
  RpcRuleSampler sampler = RpcRuleSampler.newBuilder()
      .putRule(methodEquals("health"), Sampler.ALWAYS_SAMPLE)
      .build();

  @Test public void matches() {
    Map<Sampler, Boolean> samplerToAnswer = new LinkedHashMap<>();
    samplerToAnswer.put(Sampler.ALWAYS_SAMPLE, true);
    samplerToAnswer.put(Sampler.NEVER_SAMPLE, false);

    samplerToAnswer.forEach((sampler, answer) -> {
      this.sampler = RpcRuleSampler.newBuilder()
          .putRule(methodEquals("health"), sampler)
          .build();

      when(request.method()).thenReturn("health");

      assertThat(this.sampler.trySample(request))
          .isEqualTo(answer);

      // consistent answer
      assertThat(this.sampler.trySample(request))
          .isEqualTo(answer);
    });
  }

  @Test public void nullOnNull() {
    assertThat(sampler.trySample(null))
        .isNull();
  }

  @Test public void unmatched() {
    sampler = RpcRuleSampler.newBuilder()
        .putRule(methodEquals("log"), Sampler.ALWAYS_SAMPLE)
        .build();

    assertThat(sampler.trySample(request))
        .isNull();

    when(request.method()).thenReturn("health");

    // consistent answer
    assertThat(sampler.trySample(request))
        .isNull();
  }

  @Test public void exampleCustomMatcher() {
    Matcher<RpcRequest> playInTheUSA = request -> (!"health".equals(request.method()));

    sampler = RpcRuleSampler.newBuilder()
        .putRule(playInTheUSA, RateLimitingSampler.create(100))
        .build();

    when(request.method()).thenReturn("log");

    assertThat(sampler.trySample(request))
        .isTrue();

    when(request.method()).thenReturn("health");

    assertThat(sampler.trySample(request))
        .isNull(); // unmatched because country is health
  }

  @Test public void putAllRules() {
    RpcRuleSampler base = RpcRuleSampler.newBuilder()
        .putRule(methodEquals("health"), Sampler.NEVER_SAMPLE)
        .build();

    sampler = RpcRuleSampler.newBuilder()
        .putAllRules(base)
        .build();

    when(request.method()).thenReturn("log");

    assertThat(sampler.trySample(request))
        .isNull();
  }

  // empty may sound unintuitive, but it allows use of the same type when always deferring
  @Test public void noRulesOk() {
    RpcRuleSampler.newBuilder().build();
  }
}
