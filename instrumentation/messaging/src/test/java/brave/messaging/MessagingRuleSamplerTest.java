/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging;

import brave.sampler.Matcher;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.messaging.MessagingRequestMatchers.operationEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MessagingRuleSamplerTest {

  @Mock ConsumerRequest request;

  MessagingRuleSampler sampler = MessagingRuleSampler.newBuilder()
    .putRule(operationEquals("receive"), Sampler.ALWAYS_SAMPLE)
    .build();

  @Test void matches() {
    Map<Sampler, Boolean> samplerToAnswer = new LinkedHashMap<>();
    samplerToAnswer.put(Sampler.ALWAYS_SAMPLE, true);
    samplerToAnswer.put(Sampler.NEVER_SAMPLE, false);

    samplerToAnswer.forEach((sampler, answer) -> {
      this.sampler = MessagingRuleSampler.newBuilder()
        .putRule(operationEquals("receive"), sampler)
        .build();

      when(request.operation()).thenReturn("receive");

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
    sampler = MessagingRuleSampler.newBuilder()
      .putRule(operationEquals("send"), Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.trySample(request))
      .isNull();

    when(request.operation()).thenReturn("receive");

    // consistent answer
    assertThat(sampler.trySample(request))
      .isNull();
  }

  @Test void exampleCustomMatcher() {
    Matcher<MessagingRequest> playInTheUSA = request -> (!"receive".equals(request.operation()));

    sampler = MessagingRuleSampler.newBuilder()
      .putRule(playInTheUSA, RateLimitingSampler.create(100))
      .build();

    when(request.operation()).thenReturn("send");

    assertThat(sampler.trySample(request))
      .isTrue();

    when(request.operation()).thenReturn("receive");

    assertThat(sampler.trySample(request))
      .isNull(); // unmatched because operation is receive
  }

  @Test void putAllRules() {
    MessagingRuleSampler base = MessagingRuleSampler.newBuilder()
      .putRule(operationEquals("receive"), Sampler.NEVER_SAMPLE)
      .build();

    sampler = MessagingRuleSampler.newBuilder()
      .putAllRules(base)
      .build();

    when(request.operation()).thenReturn("send");

    assertThat(sampler.trySample(request))
      .isNull();
  }

  // empty may sound unintuitive, but it allows use of the same type when always deferring
  @Test void noRulesOk() {
    MessagingRuleSampler.newBuilder().build();
  }
}
