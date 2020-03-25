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
package brave.spring.rabbit;

import brave.Tracing;
import brave.messaging.MessagingRuleSampler;
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import org.junit.Test;

import static brave.messaging.MessagingRequestMatchers.operationEquals;

public class ITSpringRabbitCustomSampling_Producer extends ITSpringRabbit {
  @Override MessagingTracing messagingTracing(Tracing tracing) {
    return MessagingTracing.newBuilder(tracing)
      .producerSampler(MessagingRuleSampler.newBuilder()
        .putRule(operationEquals("send"), Sampler.NEVER_SAMPLE)
        .build())
      .build();
  }

  @Test public void customSampler() {
    produceMessage();
    awaitMessageConsumed();

    // since the producer was unsampled, the consumer should be unsampled also due to propagation
    // reporter rules verify nothing was reported
  }
}
