package brave.spring.rabbit;

import brave.Tracing;
import brave.propagation.StrictScopeDecorator;
import brave.propagation.ThreadLocalCurrentTraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
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
import org.springframework.context.ApplicationContext;
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

  @Rule public TestName testName = new TestName();

  @ClassRule public static BrokerRunning brokerRunning = BrokerRunning.isRunning();

  static ITSpringAmqpTracingTestFixture testFixture;

  @BeforeClass public static void setupTestFixture() {
    testFixture = new ITSpringAmqpTracingTestFixture();
  }

  @AfterClass public static void close() {
    Tracing.current().close();
  }

  @Before public void reset() {
    testFixture.reset();
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

    Message capturedMessage = testFixture.capturedMessage();
    Map<String, Object> headers = capturedMessage.getMessageProperties().getHeaders();
    assertThat(headers.keySet()).containsExactly("not-zipkin-header");
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
  public static class CommonRabbitConfig {
    @Bean
    public ConnectionFactory connectionFactory() {
      CachingConnectionFactory result = new CachingConnectionFactory();
      result.setAddresses("127.0.0.1");
      return result;
    }

    @Bean
    public Exchange exchange() {
      return ExchangeBuilder.topicExchange("test-exchange").durable(true).build();
    }

    @Bean
    public Queue queue() {
      return new Queue("test-queue");
    }

    @Bean
    public Binding binding(Exchange exchange, Queue queue) {
      return BindingBuilder.bind(queue).to(exchange).with("test.binding").noargs();
    }

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
      return new RabbitAdmin(connectionFactory);
    }
  }

  @Configuration
  public static class RabbitProducerConfig {
    @Bean
    public Tracing tracing(BlockingQueue<Span> producerSpans) {
      return Tracing.newBuilder()
          .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
              .addScopeDecorator(StrictScopeDecorator.create())
              .build())
          .localServiceName("spring-amqp-producer")
          .spanReporter(producerSpans::add)
          .build();
    }

    @Bean
    public SpringRabbitTracing springRabbitTracing(Tracing tracing) {
      return SpringRabbitTracing.create(tracing);
    }

    @Bean
    public BlockingQueue<Span> producerSpans() {
      return new LinkedBlockingQueue<>();
    }

    @Bean
    public RabbitTemplate newRabbitTemplate(
        ConnectionFactory connectionFactory,
        SpringRabbitTracing springRabbitTracing
    ) {
      RabbitTemplate newRabbitTemplate = springRabbitTracing.newRabbitTemplate(connectionFactory);
      newRabbitTemplate.setExchange("test-exchange");
      return newRabbitTemplate;
    }

    @Bean
    public RabbitTemplate decorateRabbitTemplate(
        ConnectionFactory connectionFactory,
        SpringRabbitTracing springRabbitTracing
    ) {
      RabbitTemplate newRabbitTemplate = new RabbitTemplate(connectionFactory);
      newRabbitTemplate.setExchange("test-exchange");
      return springRabbitTracing.decorateRabbitTemplate(newRabbitTemplate);
    }

    @Bean
    public HelloWorldProducer tracingRabbitProducer_new(
        @Qualifier("newRabbitTemplate") RabbitTemplate newRabbitTemplate
    ) {
      return new HelloWorldProducer(newRabbitTemplate);
    }

    @Bean
    public HelloWorldProducer tracingRabbitProducer_decorate(
        @Qualifier("decorateRabbitTemplate") RabbitTemplate newRabbitTemplate
    ) {
      return new HelloWorldProducer(newRabbitTemplate);
    }
  }

  @EnableRabbit
  @Configuration
  public static class RabbitConsumerConfig {
    @Bean
    public Tracing tracing(BlockingQueue<Span> consumerSpans) {
      return Tracing.newBuilder()
          .localServiceName("spring-amqp-consumer")
          .currentTraceContext(ThreadLocalCurrentTraceContext.newBuilder()
              .addScopeDecorator(StrictScopeDecorator.create())
              .build())
          .spanReporter(consumerSpans::add)
          .build();
    }

    @Bean
    public SpringRabbitTracing springRabbitTracing(Tracing tracing) {
      return SpringRabbitTracing.create(tracing);
    }

    @Bean
    public BlockingQueue<Span> consumerSpans() {
      return new LinkedBlockingQueue<>();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
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

    @Bean
    public HelloWorldConsumer helloWorldRabbitConsumer() {
      return new HelloWorldConsumer();
    }
  }

  private static class HelloWorldProducer {
    private final RabbitTemplate newRabbitTemplate;

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

  private static class HelloWorldConsumer {
    private CountDownLatch countDownLatch;
    private Message capturedMessage;

    HelloWorldConsumer() {
      this.countDownLatch = new CountDownLatch(1);
    }

    @RabbitListener(queues = "test-queue")
    public void testReceiveRabbit(Message message) {
      this.capturedMessage = message;
      this.countDownLatch.countDown();
    }

    public void reset() {
      this.countDownLatch = new CountDownLatch(1);
      this.capturedMessage = null;
    }

    public CountDownLatch getCountDownLatch() {
      return countDownLatch;
    }
  }

  private static class ITSpringAmqpTracingTestFixture {
    ApplicationContext producerContext;
    ApplicationContext consumerContext;
    BlockingQueue<Span> producerSpans;
    BlockingQueue<Span> consumerSpans;

    ITSpringAmqpTracingTestFixture() {
      producerContext = producerSpringContext();
      consumerContext = consumerSpringContext();
      producerSpans = (BlockingQueue<Span>) producerContext.getBean("producerSpans");
      consumerSpans = (BlockingQueue<Span>) consumerContext.getBean("consumerSpans");
    }

    private void reset() {
      HelloWorldConsumer consumer = consumerContext.getBean(HelloWorldConsumer.class);
      consumer.reset();
      producerSpans.clear();
      consumerSpans.clear();
    }

    private ApplicationContext producerSpringContext() {
      return createContext(CommonRabbitConfig.class, RabbitProducerConfig.class);
    }

    private ApplicationContext createContext(Class... configurationClasses) {
      AnnotationConfigApplicationContext producerContext = new AnnotationConfigApplicationContext();
      producerContext.register(configurationClasses);
      producerContext.refresh();
      return producerContext;
    }

    private ApplicationContext consumerSpringContext() {
      return createContext(CommonRabbitConfig.class, RabbitConsumerConfig.class);
    }

    private void produceMessage() {
      HelloWorldProducer rabbitProducer =
          producerContext.getBean("tracingRabbitProducer_new", HelloWorldProducer.class);
      rabbitProducer.send();
    }

    private void produceMessageFromDefault() {
      HelloWorldProducer rabbitProducer =
          producerContext.getBean("tracingRabbitProducer_decorate", HelloWorldProducer.class);
      rabbitProducer.send();
    }

    private void awaitMessageConsumed() throws InterruptedException {
      HelloWorldConsumer consumer = consumerContext.getBean(HelloWorldConsumer.class);
      consumer.getCountDownLatch().await();
    }

    private Message capturedMessage() {
      HelloWorldConsumer consumer = consumerContext.getBean(HelloWorldConsumer.class);
      return consumer.capturedMessage;
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
