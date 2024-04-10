/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.messaging.features;

import brave.Tracing;
import brave.messaging.MessagingRuleSampler;
import brave.messaging.MessagingTracing;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunctions;
import org.junit.jupiter.api.Test;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static brave.messaging.MessagingRequestMatchers.operationEquals;
import static org.mockito.Mockito.mock;

public class ExampleTest {
  Tracing tracing = mock(Tracing.class);
  MessagingTracing messagingTracing;

  // This mainly shows that we don't accidentally rely on package-private access
  @Test void showConstruction() {
    messagingTracing = MessagingTracing.newBuilder(tracing)
      .consumerSampler(MessagingRuleSampler.newBuilder()
        .putRule(channelNameEquals("alerts"), Sampler.NEVER_SAMPLE)
        .putRule(operationEquals("receive"), RateLimitingSampler.create(100))
        .build())
      .producerSampler(SamplerFunctions.neverSample())
      .build();
  }
}
