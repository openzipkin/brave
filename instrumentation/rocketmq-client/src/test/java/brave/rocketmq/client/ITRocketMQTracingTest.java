/*
 * Copyright 2013-2024 The OpenZipkin Authors
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
package brave.rocketmq.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeOrderlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import brave.Span;
import brave.handler.MutableSpan;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import brave.test.ITRemote;
import brave.test.IntegrationTestSpanHandler;

@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@Timeout(60)
class ITRocketMQTracingTest extends ITRemote {
  @Container static RocketMQContainer rocketMQ = new RocketMQContainer();

  IntegrationTestSpanHandler producerSpanHandler = new IntegrationTestSpanHandler();
  IntegrationTestSpanHandler consumerSpanHandler = new IntegrationTestSpanHandler();

  SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
  SamplerFunction<MessagingRequest> consumerSampler = SamplerFunctions.deferDecision();

  RocketMQTracing producerTracing =
    RocketMQTracing.create(MessagingTracing
      .newBuilder(
        tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("producer").clearSpanHandlers()
          .addSpanHandler(producerSpanHandler).build())
      .producerSampler(r -> producerSampler.trySample(r)).build());

  RocketMQTracing consumerTracing =
    RocketMQTracing.create(MessagingTracing
      .newBuilder(
        tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("consumer").clearSpanHandlers()
          .addSpanHandler(consumerSpanHandler).build())
      .consumerSampler(r -> consumerSampler.trySample(r)).build());

  @Test void send() throws Exception {
    String topic = "testSend";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    DefaultMQProducer producer = new DefaultMQProducer("testSend");
    // TODO: what is this deprecated in favor of?
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new TracingSendMessageHook(producerTracing));
    producer.setNamesrvAddr(rocketMQ.getNamesrvAddr());
    producer.start();
    producer.send(message);

    producer.shutdown();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test void sendOneway() throws Exception {
    String topic = "testSendOneway";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    DefaultMQProducer producer = new DefaultMQProducer("testSendOneway");
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new TracingSendMessageHook(producerTracing));
    producer.setNamesrvAddr(rocketMQ.getNamesrvAddr());
    producer.start();
    producer.sendOneway(message);

    producer.shutdown();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test void sendAsync() throws Exception {
    String topic = "testSendAsync";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    DefaultMQProducer producer = new DefaultMQProducer("testSendAsync");
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new TracingSendMessageHook(producerTracing));
    producer.setNamesrvAddr(rocketMQ.getNamesrvAddr());
    producer.start();
    producer.send(message, new SendCallback() {
      @Override public void onSuccess(SendResult sendResult) {
      }

      @Override public void onException(Throwable e) {

      }
    });

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(Span.Kind.PRODUCER);
    producer.shutdown();
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test void tracingMessageListenerConcurrently() throws Exception {
    String topic = "tracingMessageListenerConcurrently";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    String nameserverAddr = rocketMQ.getNamesrvAddr();
    DefaultMQProducer producer = new DefaultMQProducer("tracingMessageListenerConcurrently");
    producer.setNamesrvAddr(nameserverAddr);
    producer.start();

    DefaultMQPushConsumer consumer =
      new DefaultMQPushConsumer("tracingMessageListenerConcurrently");
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.subscribe(topic, "*");
    MessageListenerConcurrently messageListenerConcurrently = consumerTracing.wrap(new MessageListenerConcurrently() {
      @Override
      public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
      }
    });
    consumer.registerMessageListener(messageListenerConcurrently);
    producer.send(message);
    consumer.start();

    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(Span.Kind.CONSUMER);

    producer.shutdown();
    consumer.shutdown();

    assertThat(consumerSpan.parentId()).isNull();
  }

  @Test void tracingMessageListenerOrderly() throws Exception {
    String topic = "tracingMessageListenerOrderly";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    String nameserverAddr = rocketMQ.getNamesrvAddr();
    DefaultMQProducer producer = new DefaultMQProducer("tracingMessageListenerOrderly");
    producer.setNamesrvAddr(nameserverAddr);
    producer.start();

    DefaultMQPushConsumer consumer =
      new DefaultMQPushConsumer("tracingMessageListenerOrderly");
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.subscribe(topic, "*");
    MessageListenerOrderly messageListenerOrderly = consumerTracing.wrap(new MessageListenerOrderly() {
      @Override
      public ConsumeOrderlyStatus consumeMessage(List<MessageExt> list, ConsumeOrderlyContext consumeOrderlyContext) {
        return ConsumeOrderlyStatus.SUCCESS;
      }
    });
    consumer.registerMessageListener(messageListenerOrderly);
    producer.send(message);
    consumer.start();

    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(Span.Kind.CONSUMER);

    producer.shutdown();
    consumer.shutdown();

    assertThat(consumerSpan.parentId()).isNull();
  }

  @Test void all() throws Exception {
    String topic = "testAll";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    String nameserverAddr = rocketMQ.getNamesrvAddr();
    DefaultMQProducer producer = new DefaultMQProducer("testAll");
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new TracingSendMessageHook(producerTracing));
    producer.setNamesrvAddr(nameserverAddr);
    producer.start();

    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testAll");
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.subscribe(topic, "*");
    MessageListenerOrderly messageListenerOrderly = consumerTracing.wrap(new MessageListenerOrderly() {
      @Override
      public ConsumeOrderlyStatus consumeMessage(List<MessageExt> list, ConsumeOrderlyContext consumeOrderlyContext) {
        return ConsumeOrderlyStatus.SUCCESS;
      }
    });
    consumer.registerMessageListener(messageListenerOrderly);

    producer.send(message);
    consumer.start();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(Span.Kind.PRODUCER);
    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(Span.Kind.CONSUMER);

    producer.shutdown();
    consumer.shutdown();

    assertThat(producerSpan.parentId()).isNull();
    assertThat(consumerSpan.parentId()).isNotNull();
    assertChildOf(consumerSpan, producerSpan);
  }
}
