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
package brave.spring.rabbit;

import brave.Tracing;
import brave.messaging.MessagingRuleSampler;
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import brave.spring.rabbit.ITSpringRabbitTracing.DefaultMessagingTracing;
import brave.spring.rabbit.ITSpringRabbitTracing.TestFixture;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.amqp.rabbit.junit.BrokerRunning;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static brave.messaging.MessagingRequestMatchers.operationEquals;

public class ITSpringRabbitCustomSampling_Producer {
  @ClassRule public static BrokerRunning brokerRunning = BrokerRunning.isRunning();

  static TestFixture testFixture;

  @BeforeClass public static void setupTestFixture() {
    testFixture = new TestFixture(
      ProducerUnsampledMessagingTracing.class,
      DefaultMessagingTracing.class
    );
  }

  @AfterClass public static void close() {
    Tracing.current().close();
  }

  @Before public void reset() {
    testFixture.reset();
  }

  @After public void noSpans() throws InterruptedException {
    // ensure our tests consumed all the spans
    testFixture.assertNoSpans();
  }

  @Configuration
  static class ProducerUnsampledMessagingTracing {
    @Bean MessagingTracing messagingTracing(Tracing tracing) {
      return MessagingTracing.newBuilder(tracing)
        .producerSampler(MessagingRuleSampler.newBuilder()
          .putRule(operationEquals("send"), Sampler.NEVER_SAMPLE)
          .build())
        .build();
    }
  }

  @Test public void customSampler() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    // since the producer was unsampled, the consumer should be unsampled also due to propagation
    // @After will check this
  }
}
