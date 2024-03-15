# brave-instrumentation-rocketmq-client

## Tracing for RocketMQ Client

This module provides instrumentation for RocketMQ based services.

## example

### producer

The key is to register our hook to the producer

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
    // todo Replaced with actual tracing construct
    Tracing tracing = Tracing.newBuilder().build();
    SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
    RocketMQTracing producerTracing = RocketMQTracing.create(
      MessagingTracing.newBuilder(tracing).producerSampler(producerSampler).build());

    String topic = "testSend";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    DefaultMQProducer producer = new DefaultMQProducer("testSend");
    // todo This is the key, register the hook to the producer
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new SendMessageBraveHookImpl(producerTracing));
    // Replace with actual address
    producer.setNamesrvAddr("127.0.0.1:9876");
    producer.start();
    producer.send(message);

    producer.shutdown();
  }
}

```

### consumer

wrap `org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently`
using `brave.rocketmq.client.RocketMQTracing.wrap(long, org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly)`,
or alternatively, wrap `org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly`
using `brave.rocketmq.client.RocketMQTracing.wrap(int, org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently)`;

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
    // todo Replaced with actual tracing construct
    Tracing tracing = Tracing.newBuilder().build();
    SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
    RocketMQTracing rocketMQTracing = RocketMQTracing.create(
      MessagingTracing.newBuilder(tracing).producerSampler(producerSampler).build());

    String topic = "testPushConsumer";
    String nameserverAddr = "127.0.0.1:9876";

    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testPushConsumer");
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.subscribe(topic, "*");
    MessageListenerConcurrently messageListenerConcurrently = rocketMQTracing.wrap(new MessageListenerConcurrently() {
      @Override
      public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> list, ConsumeConcurrentlyContext consumeConcurrentlyContext) {
        Span span =
          Optional.ofNullable(Tracing.currentTracer()).map(Tracer::currentSpan).orElse(null);
        // do something
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
      }
    });
    consumer.registerMessageListener(messageListenerConcurrently);

    consumer.start();
  }

}

```

