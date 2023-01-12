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

Replace `org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently`
with `brave.rocketmq.client.TracingMessageListenerConcurrently`
or `org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly`
with `brave.rocketmq.client.TracingMessageListenerOrderly`;

```java
package brave.rocketmq.client;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.Optional;

public class ProducerExample {

  public static void main(String[] args) throws Exception {
    // todo Replaced with actual tracing construct
    Tracing tracing = Tracing.newBuilder().build();
    SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
    RocketMQTracing producerTracing = RocketMQTracing.create(
      MessagingTracing.newBuilder(tracing).producerSampler(producerSampler).build());

    String topic = "testPushConsumer";
    String nameserverAddr = "127.0.0.1:9876";

    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testPushConsumer");
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.subscribe(topic, "*");
    consumer.registerMessageListener(new TraceableMessageListenerConcurrently(0, producerTracing) {
      @Override
      protected void handleMessage(MessageExt messageExt) {
        Span span =
          Optional.ofNullable(Tracing.currentTracer()).map(Tracer::currentSpan).orElse(null);
        // do something
      }
    });
    consumer.start();
  }
}

```

