# brave-instrumentation-rocketmq-client

## Tracing for RocketMQ Client

This module provides instrumentation for RocketMQ based services.

## example

### producer

Register `brave.rocketmq.client.RocketMQTracing.newSendMessageHook()` to trace the message.

```java
package brave.rocketmq.client;

import brave.Tracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;

public class ProducerExample {
  public static void main(String[] args) throws Exception {
    Tracing tracing = Tracing.newBuilder().build();
    SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
    RocketMQTracing producerTracing = RocketMQTracing.create(
        MessagingTracing.newBuilder(tracing).producerSampler(producerSampler).build());

    String topic = "testSend";
    Message message = new Message(topic, "zipkin", "zipkin".getBytes());
    DefaultMQProducer producer = new DefaultMQProducer("testSend");
    producer.getDefaultMQProducerImpl()
        .registerSendMessageHook(producerTracing.newSendMessageHook());
    producer.setNamesrvAddr("127.0.0.1:9876");
    producer.start();
    producer.send(message);

    producer.shutdown();
  }
}
```

### consumer

Wrap `org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly`
using `brave.rocketmq.client.RocketMQTracing.messageListenerOrderly(org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly)`,
or alternatively, wrap `org.apache.rocketmq.client.consumer.listener.messageListenerConcurrently`
using `brave.rocketmq.client.RocketMQTracing.messageListenerConcurrently(org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently)`;

```java
package brave.rocketmq.client;

import java.util.List;
import java.util.Optional;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.rocketmq.client.RocketMQTracing;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;

public class ProducerExample {
  public static void main(String[] args) throws Exception {
    Tracing tracing = Tracing.newBuilder().build();
    SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
    RocketMQTracing rocketMQTracing = RocketMQTracing.create(
        MessagingTracing.newBuilder(tracing).producerSampler(producerSampler).build());

    String topic = "testPushConsumer";
    String nameserverAddr = "127.0.0.1:9876";

    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testPushConsumer");
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.subscribe(topic, "*");
    MessageListenerConcurrently messageListenerConcurrently = rocketMQTracing.messageListenerConcurrently(
        new MessageListenerConcurrently() {
          @Override public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext context) {
            // do something
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
          }
        });
    consumer.registerMessageListener(messageListenerConcurrently);

    consumer.start();
  }
}
```