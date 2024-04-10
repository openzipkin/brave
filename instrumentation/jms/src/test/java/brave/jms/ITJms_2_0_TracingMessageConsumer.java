/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jms;

import brave.messaging.MessagingRuleSampler;
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import org.junit.jupiter.api.Test;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static org.assertj.core.api.Assertions.assertThat;

/** When adding tests here, also add to {@linkplain brave.jms.ITTracingJMSConsumer} */
class ITJms_2_0_TracingMessageConsumer extends ITJms_1_1_TracingMessageConsumer {
  @Override JmsExtension newJmsExtension() {
    return new ArtemisJmsExtension();
  }

  // Inability to encode "b3" on a received BytesMessage only applies to ActiveMQ 5.x
  @Test public void receive_resumesTrace_bytes() throws JMSException {
    receive_resumesTrace(() -> messageProducer.send(bytesMessage), messageConsumer);
  }

  @Test public void receive_customSampler() throws JMSException {
    queueReceiver.close();

    MessagingRuleSampler consumerSampler = MessagingRuleSampler.newBuilder()
      .putRule(channelNameEquals(jms.queue.getQueueName()), Sampler.NEVER_SAMPLE)
      .build();

    try (MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing)
      .consumerSampler(consumerSampler)
      .build();
         JMSContext context = JmsTracing.create(messagingTracing)
           .connectionFactory(((ArtemisJmsExtension) jms).factory)
           .createContext(JMSContext.AUTO_ACKNOWLEDGE);
         JMSConsumer consumer = context.createConsumer(jms.queue)
    ) {
      queueSender.send(message);

      // Check that the message headers are not sampled
      assertThat(consumer.receive().getStringProperty("b3"))
        .endsWith("-0");
    }

    // @After will also check that the consumer was not sampled
  }
}
