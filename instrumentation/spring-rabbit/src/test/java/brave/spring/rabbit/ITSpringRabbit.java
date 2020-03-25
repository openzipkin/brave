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
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import brave.test.ITRemote;
import brave.test.TestSpanReporter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.junit.BrokerRunning;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public abstract class ITSpringRabbit extends ITRemote {
  static final String TEST_QUEUE = "test-queue";

  @ClassRule public static BrokerRunning brokerRunning = BrokerRunning.isRunning();

  @Rule public TestSpanReporter producerReporter = new TestSpanReporter();
  @Rule public TestSpanReporter consumerReporter = new TestSpanReporter();

  SpringRabbitTracing producerTracing = SpringRabbitTracing.create(
    messagingTracing(tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("producer")
      .spanReporter(producerReporter).build())
  );

  SpringRabbitTracing consumerTracing = SpringRabbitTracing.create(
    messagingTracing(tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("consumer")
      .spanReporter(consumerReporter).build())
  );

  CachingConnectionFactory connectionFactory = new CachingConnectionFactory("127.0.0.1");
  Exchange exchange = ExchangeBuilder.topicExchange("test-exchange").durable(true).build();
  Queue queue = new Queue(TEST_QUEUE);
  Binding binding = BindingBuilder.bind(queue).to(exchange).with("test.binding").noargs();
  //HelloWorldConsumer consumer = consumerContext.getBean(HelloWorldConsumer.class);
  AnnotationConfigApplicationContext producerContext = new AnnotationConfigApplicationContext();
  AnnotationConfigApplicationContext consumerContext = new AnnotationConfigApplicationContext();

  MessagingTracing messagingTracing(Tracing tracing) {
    return MessagingTracing.create(tracing);
  }

  @Before public void refresh() {
    producerContext.registerBean(SpringRabbitTracing.class, () -> producerTracing);
    producerContext.registerBean(CachingConnectionFactory.class, () -> connectionFactory);
    producerContext.registerBean(Binding.class, () -> binding);
    producerContext.register(RabbitProducerConfig.class);
    producerContext.refresh();

    consumerContext.registerBean(SpringRabbitTracing.class, () -> consumerTracing);
    consumerContext.registerBean(CachingConnectionFactory.class, () -> connectionFactory);
    consumerContext.registerBean(Binding.class, () -> binding);
    consumerContext.register(RabbitConsumerConfig.class);
    consumerContext.refresh();
  }

  @After public void closeContext() {
    producerContext.close();
    consumerContext.close();
  }

  @Configuration
  static class RabbitProducerConfig {
    @Bean RabbitTemplate newRabbitTemplate(
      ConnectionFactory connectionFactory,
      Binding binding,
      SpringRabbitTracing springRabbitTracing
    ) {
      RabbitTemplate result = springRabbitTracing.newRabbitTemplate(connectionFactory);
      result.setExchange(binding.getExchange());
      return result;
    }

    @Bean RabbitTemplate decorateRabbitTemplate(
      ConnectionFactory connectionFactory,
      Binding binding,
      SpringRabbitTracing springRabbitTracing
    ) {
      RabbitTemplate result = new RabbitTemplate(connectionFactory);
      result.setExchange(binding.getExchange());
      return springRabbitTracing.decorateRabbitTemplate(result);
    }

    @Bean HelloWorldProducer tracingRabbitProducer_new(
      @Qualifier("newRabbitTemplate") RabbitTemplate rabbitTemplate, Binding binding
    ) {
      return new HelloWorldProducer(rabbitTemplate, binding);
    }

    @Bean HelloWorldProducer tracingRabbitProducer_decorate(
      @Qualifier("decorateRabbitTemplate") RabbitTemplate rabbitTemplate, Binding binding
    ) {
      return new HelloWorldProducer(rabbitTemplate, binding);
    }
  }

  @EnableRabbit
  @Configuration
  static class RabbitConsumerConfig {
    @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory,
      SpringRabbitTracing springRabbitTracing
    ) {
      SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory =
        new SimpleRabbitListenerContainerFactory();
      rabbitListenerContainerFactory.setConnectionFactory(connectionFactory);
      return springRabbitTracing.decorateSimpleRabbitListenerContainerFactory(
        rabbitListenerContainerFactory
      );
    }

    @Bean HelloWorldConsumer helloWorldRabbitConsumer() {
      return new HelloWorldConsumer();
    }
  }

  static class HelloWorldProducer {
    final RabbitTemplate rabbitTemplate;
    final Binding binding;

    HelloWorldProducer(RabbitTemplate rabbitTemplate, Binding binding) {
      this.rabbitTemplate = rabbitTemplate;
      this.binding = binding;
    }

    void send() {
      byte[] messageBody = "hello world".getBytes();
      MessageProperties properties = new MessageProperties();
      properties.setHeader("not-zipkin-header", "fakeValue");
      Message message = MessageBuilder.withBody(messageBody).andProperties(properties).build();
      rabbitTemplate.send(binding.getRoutingKey(), message);
    }
  }

  static class HelloWorldConsumer {
    CountDownLatch countDownLatch;
    Message capturedMessage;

    HelloWorldConsumer() {
      this.countDownLatch = new CountDownLatch(1);
    }

    @RabbitListener(queues = TEST_QUEUE)
    void testReceiveRabbit(Message message) {
      this.capturedMessage = message;
      this.countDownLatch.countDown();
    }

    CountDownLatch getCountDownLatch() {
      return countDownLatch;
    }
  }

  void produceMessage() {
    HelloWorldProducer rabbitProducer =
      producerContext.getBean("tracingRabbitProducer_new", HelloWorldProducer.class);
    rabbitProducer.send();
  }

  void produceMessageFromDefault() {
    HelloWorldProducer rabbitProducer =
      producerContext.getBean("tracingRabbitProducer_decorate", HelloWorldProducer.class);
    rabbitProducer.send();
  }

  void produceUntracedMessage() {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setExchange(exchange.getName());
    new HelloWorldProducer(rabbitTemplate, binding).send();
  }

  Message awaitMessageConsumed() {
    HelloWorldConsumer consumer = consumerContext.getBean(HelloWorldConsumer.class);
    try {
      consumer.getCountDownLatch().await(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    return consumer.capturedMessage;
  }
}
