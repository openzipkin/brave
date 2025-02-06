/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import brave.handler.MutableSpan;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import brave.test.ITRemote;
import brave.test.IntegrationTestSpanHandler;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static brave.Span.Kind.CONSUMER;
import static brave.Span.Kind.PRODUCER;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@Timeout(240)
class ITRocketMQTracing extends ITRemote {
  static final Logger LOGGER = LoggerFactory.getLogger(ITRocketMQTracing.class);

  @Container RocketMQContainer rocket = new RocketMQContainer();
  @RegisterExtension IntegrationTestSpanHandler producerSpanHandler = new IntegrationTestSpanHandler();
  @RegisterExtension IntegrationTestSpanHandler consumerSpanHandler = new IntegrationTestSpanHandler();

  SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
  SamplerFunction<MessagingRequest> consumerSampler = SamplerFunctions.deferDecision();

  RocketMQTracing producerTracing = RocketMQTracing.create(
      MessagingTracing.newBuilder(tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("producer")
              .clearSpanHandlers().addSpanHandler(producerSpanHandler).build())
          .producerSampler(r -> producerSampler.trySample(r))
          .build()
  );
  RocketMQTracing consumerTracing = RocketMQTracing.create(
      MessagingTracing.newBuilder(tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("consumer")
              .clearSpanHandlers().addSpanHandler(consumerSpanHandler).build())
          .consumerSampler(r -> consumerSampler.trySample(r))
          .build()
  );

  DefaultMQProducer producer;

  @BeforeEach void setup() throws Exception {
    producer = new DefaultMQProducer(testName);
    producer.setInstanceName(testName);
    producer.setNamesrvAddr(rocket.getNamesrvAddr());
    producer.getDefaultMQProducerImpl().registerSendMessageHook(new TracingSendMessage(producerTracing));
    producer.setSendMsgTimeout(60_000);
    producer.start();
    Thread.sleep(5000); // wait for producer to be ready.
  }

  @Override @AfterEach protected void close() throws Exception {
    if (producer != null) producer.shutdown();
    super.close();
  }

  @Test void send_message() throws Exception {
    producer.send(new Message(testName, testName.getBytes()));
    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test void send_one_way_message() throws Exception {
    producer.sendOneway(new Message(testName, testName.getBytes()));
    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test void async_send_message() throws Exception {
    producer.send(new Message(testName, testName.getBytes()), new SendCallback() {
      @Override public void onSuccess(SendResult sendResult) {
        LOGGER.info("Send message success: {}.", sendResult);
      }

      @Override public void onException(Throwable e) {
        assertThat(e).isNull();
      }
    });
    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test void message_listener_concurrently() throws Exception {
    producer.send(new Message(testName, testName.getBytes()));
    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(testName);
    consumer.setNamesrvAddr(rocket.getNamesrvAddr());
    consumer.subscribe(testName, "*");
    MessageListenerConcurrently messageListenerConcurrently = consumerTracing.messageListenerConcurrently(
        (list, context) -> ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
    consumer.registerMessageListener(messageListenerConcurrently);
    consumer.start();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(CONSUMER);
    assertChildOf(consumerSpan, producerSpan);
    MutableSpan listenerSpan = consumerSpanHandler.takeLocalSpan();
    assertChildOf(listenerSpan, consumerSpan);
  }

  @Test void message_listener_orderly() throws Exception {
    producer.send(new Message(testName, testName.getBytes()));
    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(testName);
    consumer.setNamesrvAddr(rocket.getNamesrvAddr());
    consumer.subscribe(testName, "*");
    MessageListenerOrderly messageListenerOrderly = consumerTracing.messageListenerOrderly(
        (list, context) -> ConsumeOrderlyStatus.SUCCESS);
    consumer.registerMessageListener(messageListenerOrderly);
    consumer.start();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(CONSUMER);
    assertChildOf(consumerSpan, producerSpan);
    MutableSpan listenerSpan = consumerSpanHandler.takeLocalSpan();
    assertChildOf(listenerSpan, consumerSpan);
  }
}
