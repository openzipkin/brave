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
package brave.spring.rabbit;

import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import brave.test.ITRemote;
import brave.test.IntegrationTestSpanHandler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.SendTo;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.amqp.core.BindingBuilder.bind;
import static org.springframework.amqp.core.ExchangeBuilder.topicExchange;
import static org.testcontainers.utility.DockerImageName.parse;

@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@Timeout(60)
abstract class ITSpringRabbit extends ITRemote {
  static final Logger LOGGER = LoggerFactory.getLogger(ITSpringRabbit.class);

  static final String TEST_QUEUE = "test-queue";
  static final Exchange exchange = topicExchange("test-exchange").durable(true).build();
  static final Queue queue = new Queue(TEST_QUEUE);
  static final Binding binding = bind(queue).to(exchange).with("test.binding").noargs();

  // for batch
  static final String TEST_QUEUE_BATCH = "test-queue-1";
  static final Exchange exchange_batch = topicExchange("test-exchange-1").durable(true).build();
  static final Queue queue_batch = new Queue(TEST_QUEUE_BATCH);
  static final Binding binding_batch =
    bind(queue_batch).to(exchange_batch).with("test.binding.1").noargs();

  // for request-reply
  static final String TEST_EXCHANGE_REQUEST_REPLY = "test-exchange-request-reply";
  static final String TEST_QUEUE_REQUEST = "test-queue-request";
  static final String TEST_QUEUE_REPLY = "test-queue-reply";
  static final Exchange exchange_request_reply =
    topicExchange(TEST_EXCHANGE_REQUEST_REPLY).durable(true).build();
  static final Queue queue_request = new Queue(TEST_QUEUE_REQUEST);
  static final Binding binding_request =
    bind(queue_request).to(exchange_request_reply).with("test.binding.request").noargs();
  static final Queue queue_reply = new Queue(TEST_QUEUE_REPLY);
  static final Binding binding_reply =
    bind(queue_reply).to(exchange_request_reply).with("test.binding.reply").noargs();

  static final int RABBIT_PORT = 5672;

  @Container RabbitMQContainer rabbit = new RabbitMQContainer();


  static final class RabbitMQContainer extends GenericContainer<RabbitMQContainer> {
    RabbitMQContainer() {
      super(parse("ghcr.io/openzipkin/zipkin-rabbitmq:3.1.1"));
      withExposedPorts(RABBIT_PORT);
      waitStrategy = Wait.forLogMessage(".*Server startup complete.*", 1);
      withStartupTimeout(Duration.ofSeconds(60));
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }

    @Override public void start() {
      super.start();

      CachingConnectionFactory connectionFactory =
        new CachingConnectionFactory(getHost(), getMappedPort(RABBIT_PORT));
      try {
        RabbitAdmin amqpAdmin = new RabbitAdmin(connectionFactory);
        amqpAdmin.declareExchange(exchange);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);

        amqpAdmin.declareExchange(exchange_batch);
        amqpAdmin.declareQueue(queue_batch);
        amqpAdmin.declareBinding(binding_batch);

        amqpAdmin.declareExchange(exchange_request_reply);
        amqpAdmin.declareQueue(queue_request);
        amqpAdmin.declareQueue(queue_reply);
        amqpAdmin.declareBinding(binding_request);
        amqpAdmin.declareBinding(binding_reply);
      } finally {
        connectionFactory.destroy();
      }
    }
  }

  @Rule public IntegrationTestSpanHandler producerSpanHandler = new IntegrationTestSpanHandler();
  @Rule public IntegrationTestSpanHandler consumerSpanHandler = new IntegrationTestSpanHandler();

  SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
  SamplerFunction<MessagingRequest> consumerSampler = SamplerFunctions.deferDecision();

  SpringRabbitTracing producerTracing = SpringRabbitTracing.create(
    MessagingTracing.newBuilder(tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("producer")
        .clearSpanHandlers().addSpanHandler(producerSpanHandler).build())
      .producerSampler(r -> producerSampler.trySample(r))
      .build()
  );

  SpringRabbitTracing consumerTracing = SpringRabbitTracing.create(
    MessagingTracing.newBuilder(tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("consumer")
        .clearSpanHandlers().addSpanHandler(consumerSpanHandler).build())
      .consumerSampler(r -> consumerSampler.trySample(r))
      .build()
  );

  CachingConnectionFactory connectionFactory;
  AnnotationConfigApplicationContext producerContext = new AnnotationConfigApplicationContext();
  AnnotationConfigApplicationContext consumerContext = new AnnotationConfigApplicationContext();

  @BeforeEach void refresh() {
    connectionFactory =
      new CachingConnectionFactory(rabbit.getHost(), rabbit.getMappedPort(RABBIT_PORT));
    producerContext.registerBean(SpringRabbitTracing.class, () -> producerTracing);
    producerContext.registerBean(CachingConnectionFactory.class, () -> connectionFactory);
    producerContext.registerBean(Binding.class, () -> binding);
    producerContext.register(RabbitProducerConfig.class);
    producerContext.registerBean("binding_batch", Binding.class, () -> binding_batch);
    producerContext.registerBean("binding_request", Binding.class, () -> binding_request);
    producerContext.registerBean("binding_reply", Binding.class, () -> binding_reply);
    producerContext.refresh();

    consumerContext.registerBean(SpringRabbitTracing.class, () -> consumerTracing);
    consumerContext.registerBean(CachingConnectionFactory.class, () -> connectionFactory);
    consumerContext.registerBean(Binding.class, () -> binding);
    consumerContext.register(RabbitConsumerConfig.class);
    consumerContext.registerBean("binding_batch", Binding.class, () -> binding_batch);
    consumerContext.registerBean("binding_request", Binding.class, () -> binding_request);
    consumerContext.registerBean("binding_reply", Binding.class, () -> binding_reply);
    consumerContext.refresh();
  }

  @AfterEach void closeContext() {
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

    @Bean SimpleRabbitListenerContainerFactory consumerBatchContainerFactory(
      ConnectionFactory connectionFactory,
      SpringRabbitTracing springRabbitTracing
    ) {
      SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory =
        new SimpleRabbitListenerContainerFactory();
      rabbitListenerContainerFactory.setConnectionFactory(connectionFactory);
      rabbitListenerContainerFactory.setConsumerBatchEnabled(true);
      rabbitListenerContainerFactory.setBatchListener(true);
      rabbitListenerContainerFactory.setBatchSize(3);
      return springRabbitTracing.decorateSimpleRabbitListenerContainerFactory(
        rabbitListenerContainerFactory
      );
    }

    @Bean BatchConsumer batchRabbitConsumer() {
      return new BatchConsumer();
    }

    @Bean RequestConsumer requestConsumer() {
      return new RequestConsumer();
    }

    @Bean ReplyConsumer replyConsumer() {
      return new ReplyConsumer();
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

  static class BatchConsumer {
    CountDownLatch countDownLatch;
    List<Message> capturedMessages;

    BatchConsumer() {
      this.countDownLatch = new CountDownLatch(1);
    }

    @RabbitListener(queues = TEST_QUEUE_BATCH, containerFactory = "consumerBatchContainerFactory")
    void testReceiveRabbit(List<Message> messages) {
      this.capturedMessages = messages;
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

  static class RequestConsumer {

    @RabbitListener(queues = TEST_QUEUE_REQUEST)
    @SendTo(TEST_QUEUE_REPLY)
    String testReceiveRabbit(Message message) {
      return new String(message.getBody()) + " test reply";
    }
  }

  void produceUntracedMessage() {
    produceUntracedMessage(exchange.getName(), binding);
  }

  void produceUntracedMessage(String exchange, Binding binding) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setExchange(exchange);
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

  List<Message> awaitBatchMessageConsumed() {
    BatchConsumer consumer = consumerContext.getBean(BatchConsumer.class);
    try {
      consumer.getCountDownLatch().await(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    return consumer.capturedMessages;
  }

  Message awaitReplyMessageConsumed() {
    ReplyConsumer consumer = consumerContext.getBean(ReplyConsumer.class);
    try {
      consumer.getCountDownLatch().await(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
    return consumer.capturedMessage;
  }

  static class ReplyConsumer {
    CountDownLatch countDownLatch;
    Message capturedMessage;

    ReplyConsumer() {
      this.countDownLatch = new CountDownLatch(1);
    }

    @RabbitListener(queues = TEST_QUEUE_REPLY)
    void testReceiveRabbit(Message message) {
      this.capturedMessage = message;
      this.countDownLatch.countDown();
    }

    CountDownLatch getCountDownLatch() {
      return countDownLatch;
    }
  }
}
