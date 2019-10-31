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
import brave.messaging.MessagingTracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.amqp.core.AmqpAdmin;
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
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.junit.BrokerRunning;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.internal.DependencyLinker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;
import static zipkin2.Span.Kind.CONSUMER;
import static zipkin2.Span.Kind.PRODUCER;

public class ITSpringRabbitTracing {
  @ClassRule public static BrokerRunning brokerRunning = BrokerRunning.isRunning();

  static TestFixture testFixture;

  @BeforeClass public static void setupTestFixture() {
    testFixture = new TestFixture(DefaultMessagingTracing.class, DefaultMessagingTracing.class);
  }

  @AfterClass public static void close() {
    Tracing.current().close();
    testFixture.close();
  }

  @Before public void reset() {
    testFixture.reset();
  }

  @After public void noSpans() throws InterruptedException {
    // ensure our tests consumed all the spans
    testFixture.assertNoSpans();
  }

  @Test public void propagates_trace_info_across_amqp_from_producer() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    List<Span> allSpans = new ArrayList<>();
    allSpans.add(takeProducerSpan());
    allSpans.add(takeConsumerSpan());
    allSpans.add(takeConsumerSpan());

    String originatingTraceId = allSpans.get(0).traceId();
    String consumerSpanId = allSpans.get(1).id();

    assertThat(allSpans)
      .extracting(Span::kind, Span::traceId, Span::parentId)
      .containsExactly(
        tuple(PRODUCER, originatingTraceId, null),
        tuple(CONSUMER, originatingTraceId, originatingTraceId),
        tuple(null, originatingTraceId, consumerSpanId)
      );
  }

  @Test public void clears_message_headers_after_propagation() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    Message capturedMessage = testFixture.awaitMessageConsumed();
    Map<String, Object> headers = capturedMessage.getMessageProperties().getHeaders();
    assertThat(headers.keySet()).containsExactly("not-zipkin-header");

    takeProducerSpan();
    takeConsumerSpan();
    takeConsumerSpan(); // listener
  }

  @Test public void tags_spans_with_exchange_and_routing_key() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    List<Span> consumerSpans = new ArrayList<>();
    consumerSpans.add(takeConsumerSpan());
    consumerSpans.add(takeConsumerSpan());

    assertThat(consumerSpans)
      .filteredOn(s -> s.kind() == CONSUMER)
      .flatExtracting(s -> s.tags().entrySet())
      .containsOnly(
        entry("rabbit.exchange", "test-exchange"),
        entry("rabbit.routing_key", "test.binding"),
        entry("rabbit.queue", "test-queue")
      );

    assertThat(consumerSpans)
      .filteredOn(s -> s.kind() != CONSUMER)
      .flatExtracting(s -> s.tags().entrySet())
      .isEmpty();

    takeProducerSpan();
  }

  /** Technical implementation of clock sharing might imply a race. This ensures happens-after */
  @Test public void listenerSpanHappensAfterConsumerSpan() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    Span span1 = takeConsumerSpan(), span2 = takeConsumerSpan();
    Span consumerSpan = span1.kind() == Span.Kind.CONSUMER ? span1 : span2;
    Span listenerSpan = consumerSpan == span1 ? span2 : span1;

    assertThat(consumerSpan.timestampAsLong() + consumerSpan.durationAsLong())
      .isLessThanOrEqualTo(listenerSpan.timestampAsLong());

    takeProducerSpan();
  }

  @Test public void creates_dependency_links() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    List<Span> allSpans = new ArrayList<>();
    allSpans.add(takeProducerSpan());
    allSpans.add(takeConsumerSpan());
    allSpans.add(takeConsumerSpan());

    List<DependencyLink> links = new DependencyLinker().putTrace(allSpans).link();
    assertThat(links).extracting("parent", "child").containsExactly(
      tuple("spring-amqp-producer", "rabbitmq"),
      tuple("rabbitmq", "spring-amqp-consumer")
    );
  }

  @Test public void tags_spans_with_exchange_and_routing_key_from_default() throws Exception {
    testFixture.produceMessageFromDefault();
    testFixture.awaitMessageConsumed();

    List<Span> consumerSpans = new ArrayList<>();
    consumerSpans.add(takeProducerSpan());
    consumerSpans.add(takeConsumerSpan());

    assertThat(consumerSpans)
      .filteredOn(s -> s.kind() == CONSUMER)
      .flatExtracting(s -> s.tags().entrySet())
      .containsOnly(
        entry("rabbit.exchange", "test-exchange"),
        entry("rabbit.routing_key", "test.binding"),
        entry("rabbit.queue", "test-queue")
      );

    assertThat(consumerSpans)
      .filteredOn(s -> s.kind() != CONSUMER)
      .flatExtracting(s -> s.tags().entrySet())
      .isEmpty();

    takeConsumerSpan(); // listener
  }

  // We will revisit this eventually, but these names mostly match the method names
  @Test public void method_names_as_span_names() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    List<Span> allSpans = new ArrayList<>();
    allSpans.add(takeProducerSpan());
    allSpans.add(takeConsumerSpan());
    allSpans.add(takeConsumerSpan());

    assertThat(allSpans)
      .extracting(Span::name)
      .containsExactly("publish", "next-message", "on-message");
  }

  @Configuration
  static class DefaultMessagingTracing {
    @Bean MessagingTracing messagingTracing(Tracing tracing) {
      return MessagingTracing.create(tracing);
    }
  }

  @Configuration
  static class CommonRabbitConfig {
    @Bean ConnectionFactory connectionFactory() {
      CachingConnectionFactory result = new CachingConnectionFactory();
      result.setAddresses("127.0.0.1");
      return result;
    }

    @Bean Exchange exchange() {
      return ExchangeBuilder.topicExchange("test-exchange").durable(true).build();
    }

    @Bean Queue queue() {
      return new Queue("test-queue");
    }

    @Bean Binding binding(Exchange exchange, Queue queue) {
      return BindingBuilder.bind(queue).to(exchange).with("test.binding").noargs();
    }

    @Bean AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
      return new RabbitAdmin(connectionFactory);
    }
  }

  @Configuration
  static class RabbitProducerConfig {
    @Bean Tracing tracing(BlockingQueue<Span> producerSpans) {
      return Tracing.newBuilder()
        .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
        .localServiceName("spring-amqp-producer")
        .spanReporter(producerSpans::add)
        .build();
    }

    @Bean SpringRabbitTracing springRabbitTracing(MessagingTracing messagingTracing) {
      return SpringRabbitTracing.create(messagingTracing);
    }

    @Bean BlockingQueue<Span> producerSpans() {
      return new LinkedBlockingQueue<>();
    }

    @Bean RabbitTemplate newRabbitTemplate(
      ConnectionFactory connectionFactory,
      SpringRabbitTracing springRabbitTracing
    ) {
      RabbitTemplate newRabbitTemplate = springRabbitTracing.newRabbitTemplate(connectionFactory);
      newRabbitTemplate.setExchange("test-exchange");
      return newRabbitTemplate;
    }

    @Bean RabbitTemplate decorateRabbitTemplate(
      ConnectionFactory connectionFactory,
      SpringRabbitTracing springRabbitTracing
    ) {
      RabbitTemplate newRabbitTemplate = new RabbitTemplate(connectionFactory);
      newRabbitTemplate.setExchange("test-exchange");
      return springRabbitTracing.decorateRabbitTemplate(newRabbitTemplate);
    }

    @Bean HelloWorldProducer tracingRabbitProducer_new(
      @Qualifier("newRabbitTemplate") RabbitTemplate newRabbitTemplate
    ) {
      return new HelloWorldProducer(newRabbitTemplate);
    }

    @Bean HelloWorldProducer tracingRabbitProducer_decorate(
      @Qualifier("decorateRabbitTemplate") RabbitTemplate newRabbitTemplate
    ) {
      return new HelloWorldProducer(newRabbitTemplate);
    }
  }

  @EnableRabbit
  @Configuration
  static class RabbitConsumerConfig {
    @Bean Tracing tracing(BlockingQueue<Span> consumerSpans) {
      return Tracing.newBuilder()
        .localServiceName("spring-amqp-consumer")
        .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
          .addScopeDecorator(StrictScopeDecorator.create())
          .build())
        .spanReporter(consumerSpans::add)
        .build();
    }

    @Bean SpringRabbitTracing springRabbitTracing(MessagingTracing messagingTracing) {
      return SpringRabbitTracing.create(messagingTracing);
    }

    @Bean BlockingQueue<Span> consumerSpans() {
      return new LinkedBlockingQueue<>();
    }

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
    final RabbitTemplate newRabbitTemplate;

    HelloWorldProducer(RabbitTemplate newRabbitTemplate) {
      this.newRabbitTemplate = newRabbitTemplate;
    }

    void send() {
      byte[] messageBody = "hello world".getBytes();
      MessageProperties properties = new MessageProperties();
      properties.setHeader("not-zipkin-header", "fakeValue");
      Message message = MessageBuilder.withBody(messageBody).andProperties(properties).build();
      newRabbitTemplate.send("test.binding", message);
    }
  }

  static class HelloWorldConsumer {
    CountDownLatch countDownLatch;
    Message capturedMessage;

    HelloWorldConsumer() {
      this.countDownLatch = new CountDownLatch(1);
    }

    @RabbitListener(queues = "test-queue")
    void testReceiveRabbit(Message message) {
      this.capturedMessage = message;
      this.countDownLatch.countDown();
    }

    void reset() {
      this.countDownLatch = new CountDownLatch(1);
      this.capturedMessage = null;
    }

    CountDownLatch getCountDownLatch() {
      return countDownLatch;
    }
  }

  static final class TestFixture implements Closeable {
    AnnotationConfigApplicationContext producerContext;
    AnnotationConfigApplicationContext consumerContext;
    BlockingQueue<Span> producerSpans;
    BlockingQueue<Span> consumerSpans;

    TestFixture(Class<?> producerMessagingTracingConfig, Class<?> consumerMessagingTracingConfig) {
      producerContext = producerSpringContext(producerMessagingTracingConfig);
      consumerContext = consumerSpringContext(consumerMessagingTracingConfig);
      producerSpans = (BlockingQueue<Span>) producerContext.getBean("producerSpans");
      consumerSpans = (BlockingQueue<Span>) consumerContext.getBean("consumerSpans");
    }

    void reset() {
      HelloWorldConsumer consumer = consumerContext.getBean(HelloWorldConsumer.class);
      consumer.reset();
      producerSpans.clear();
      consumerSpans.clear();
    }

    AnnotationConfigApplicationContext producerSpringContext(Class<?> messagingTracingConfig) {
      return createContext(messagingTracingConfig, CommonRabbitConfig.class,
        RabbitProducerConfig.class);
    }

    AnnotationConfigApplicationContext consumerSpringContext(Class<?> messagingTracingConfig) {
      return createContext(messagingTracingConfig, CommonRabbitConfig.class,
        RabbitConsumerConfig.class);
    }

    AnnotationConfigApplicationContext createContext(Class... configurationClasses) {
      AnnotationConfigApplicationContext producerContext = new AnnotationConfigApplicationContext();
      producerContext.register(configurationClasses);
      producerContext.refresh();
      return producerContext;
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
      RabbitTemplate rabbitTemplate = new RabbitTemplate(
        producerContext.getBean(ConnectionFactory.class)
      );
      rabbitTemplate.setExchange("test-exchange");
      new HelloWorldProducer(rabbitTemplate).send();
    }

    Message awaitMessageConsumed() throws InterruptedException {
      HelloWorldConsumer consumer = consumerContext.getBean(HelloWorldConsumer.class);
      consumer.getCountDownLatch().await();
      return consumer.capturedMessage;
    }

    void assertNoSpans() throws InterruptedException {
      for (BlockingQueue<Span> spans : Arrays.asList(producerSpans, consumerSpans)) {
        assertThat(spans.poll(100, TimeUnit.MILLISECONDS))
          .withFailMessage("Span remaining in queue. Check for redundant reporting")
          .isNull();
      }
    }

    @Override public void close() {
      producerContext.close();
      consumerContext.close();
    }
  }

  /** Call this to block until a span was reported */
  Span takeProducerSpan() throws InterruptedException {
    Span result = testFixture.producerSpans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
      .withFailMessage("Producer span was not reported")
      .isNotNull();
    // ensure the span finished
    assertThat(result.durationAsLong()).isPositive();
    return result;
  }

  /** Call this to block until a span was reported */
  Span takeConsumerSpan() throws InterruptedException {
    Span result = testFixture.consumerSpans.poll(3, TimeUnit.SECONDS);
    assertThat(result)
      .withFailMessage("Consumer span was not reported")
      .isNotNull();
    // ensure the span finished
    assertThat(result.durationAsLong()).isPositive();
    return result;
  }
}
