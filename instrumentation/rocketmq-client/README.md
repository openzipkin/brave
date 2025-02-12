# Brave RocketMQ Client instrumentation
This module provides instrumentation for rocketmq-client 5.x+ consumers and
producers.

## Setup
Setup the generic RocketMQ component like this:
```java
rocketmqTracing = RocketMQTracing.newBuilder(messagingTracing)
                           .remoteServiceName("my-broker")
                           .build();
```

## Sampling Policy
The default sampling policy is to use the default (trace ID) sampler for
producer and consumer requests.

You can use an [MessagingRuleSampler](../messaging/README.md) to override this
based on RocketMQ topic names.

Ex. Here's a sampler that traces 100 consumer requests per second, except for
the "alerts" topic. Other requests will use a global rate provided by the
`Tracing` component.

```java
import brave.sampler.Matchers;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;

messagingTracingBuilder.consumerSampler(MessagingRuleSampler.newBuilder()
  .putRule(channelNameEquals("alerts"), Sampler.NEVER_SAMPLE)
  .putRule(Matchers.alwaysMatch(), RateLimitingSampler.create(100))
  .build());

rocketmqTracing = RocketMQTracing.create(messagingTracing);
```

## Producer

Register `brave.rocketmq.client.RocketMQTracing.newSendMessageHook()` to trace the message.

```java
Message message = new Message("zipkin", "zipkin", "zipkin".getBytes());
DefaultMQProducer producer = new DefaultMQProducer("testSend");
producer.getDefaultMQProducerImpl()
    .registerSendMessageHook(producerTracing.newSendMessageHook());
producer.setNamesrvAddr("127.0.0.1:9876");
producer.start();
producer.send(message);

producer.shutdown();
```

## Consumer

Wrap `org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly`
using `brave.rocketmq.client.RocketMQTracing.messageListenerOrderly(org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly)`,
or alternatively, wrap `org.apache.rocketmq.client.consumer.listener.messageListenerConcurrently`
using `brave.rocketmq.client.RocketMQTracing.messageListenerConcurrently(org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently)`;

```java
DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testPushConsumer");
consumer.setNamesrvAddr("127.0.0.1:9876");
consumer.subscribe("zipkin", "*");
MessageListenerConcurrently messageListenerConcurrently = rocketMQTracing.messageListenerConcurrently(
    new MessageListenerConcurrently() {
      @Override public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext context) {
        // do something
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
      }
    });
consumer.registerMessageListener(messageListenerConcurrently);

consumer.start();
```