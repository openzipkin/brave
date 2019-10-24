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
package brave.messaging;

import brave.sampler.Matcher;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static brave.messaging.MessagingRequestMatchers.operationEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MessagingRuleSamplerTest {

  @Mock ConsumerRequest request;

  MessagingRuleSampler sampler = MessagingRuleSampler.newBuilder()
    .putRule(operationEquals("receive"), Sampler.ALWAYS_SAMPLE)
    .build();

  @Test public void matches() {
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

  @Test public void nullOnNull() {
    assertThat(sampler.trySample(null))
      .isNull();
  }

  @Test public void unmatched() {
    sampler = MessagingRuleSampler.newBuilder()
      .putRule(operationEquals("bulk-receive"), Sampler.ALWAYS_SAMPLE)
      .build();

    assertThat(sampler.trySample(request))
      .isNull();

    when(request.operation()).thenReturn("receive");

    // consistent answer
    assertThat(sampler.trySample(request))
      .isNull();
  }

  @Test public void exampleCustomMatcher() {
    Matcher<MessagingRequest> playInTheUSA = request -> (!"receive".equals(request.operation()));

    sampler = MessagingRuleSampler.newBuilder()
      .putRule(playInTheUSA, RateLimitingSampler.create(100))
      .build();

    when(request.operation()).thenReturn("bulk-receive");

    assertThat(sampler.trySample(request))
      .isTrue();

    when(request.operation()).thenReturn("receive");

    assertThat(sampler.trySample(request))
      .isNull(); // unmatched because operation is receive
  }

  @Test public void putAllRules() {
    MessagingRuleSampler base = MessagingRuleSampler.newBuilder()
      .putRule(operationEquals("receive"), Sampler.NEVER_SAMPLE)
      .build();

    sampler = MessagingRuleSampler.newBuilder()
      .putAllRules(base)
      .build();

    when(request.operation()).thenReturn("bulk-receive");

    assertThat(sampler.trySample(request))
      .isNull();
  }

  // empty may sound unintuitive, but it allows use of the same type when always deferring
  @Test public void noRulesOk() {
    MessagingRuleSampler.newBuilder().build();
  }
}
