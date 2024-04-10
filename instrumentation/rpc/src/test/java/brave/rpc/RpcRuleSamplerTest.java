/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rpc;

import brave.sampler.Matcher;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.rpc.RpcRequestMatchers.methodEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RpcRuleSamplerTest {

  @Mock RpcServerRequest request;
  RpcRuleSampler sampler = RpcRuleSampler.newBuilder()
      .putRule(methodEquals("health"), Sampler.ALWAYS_SAMPLE)
      .build();

  @Test void matches() {
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

  @Test void nullOnNull() {
    assertThat(sampler.trySample(null))
        .isNull();
  }

  @Test void unmatched() {
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

  @Test void exampleCustomMatcher() {
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

  @Test void putAllRules() {
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
  @Test void noRulesOk() {
    RpcRuleSampler.newBuilder().build();
  }
}
