package brave.spring.rabbit;

import brave.Tracing;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;
import static zipkin2.Span.Kind.CONSUMER;

public class ITSpringRabbitTracing {

  @Rule public TestName testName = new TestName();

  @ClassRule public static BrokerRunning brokerRunning = BrokerRunning.isRunning();

  static ITSpringAmqpTracingTestFixture testFixture;

  @BeforeClass public static void setupTestFixture() {
    testFixture = new ITSpringAmqpTracingTestFixture();
  }

  @Before public void reset() {
    testFixture.reset();
  }

  @Test public void propagates_trace_info_across_amqp_from_producer() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    assertThat(testFixture.producerSpans).hasSize(1);
    assertThat(testFixture.consumerSpans).hasSize(2);

    String originatingTraceId = testFixture.producerSpans.get(0).traceId();
    String consumerSpanId = testFixture.consumerSpans.get(0).id();

    assertThat(testFixture.consumerSpans)
        .extracting(Span::kind, Span::traceId, Span::parentId)
        .containsExactly(
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

    assertThat(testFixture.consumerSpans).hasSize(2);

    assertThat(testFixture.consumerSpans)
        .filteredOn(s -> s.kind() == CONSUMER)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(
            entry("rabbit.exchange", "test-exchange"),
            entry("rabbit.routing_key", "test.binding"),
            entry("rabbit.queue", "test-queue")
        );

    assertThat(testFixture.consumerSpans)
        .filteredOn(s -> s.kind() != CONSUMER)
        .flatExtracting(s -> s.tags().entrySet())
        .isEmpty();
  }

  @Test public void creates_dependency_links() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    List<Span> allSpans = new ArrayList<>();
    allSpans.addAll(testFixture.consumerSpans);
    allSpans.addAll(testFixture.producerSpans);

    List<DependencyLink> links = new DependencyLinker().putTrace(allSpans.iterator()).link();
    assertThat(links).extracting("parent", "child").containsExactly(
        tuple("spring-amqp-producer", "rabbitmq"),
        tuple("rabbitmq", "spring-amqp-consumer")
    );
  }

  @Test public void tags_spans_with_exchange_and_routing_key_from_default() throws Exception {
    testFixture.produceMessageFromDefault();
    testFixture.awaitMessageConsumed();

    assertThat(testFixture.consumerSpans).hasSize(2);

    assertThat(testFixture.consumerSpans)
        .filteredOn(s -> s.kind() == CONSUMER)
        .flatExtracting(s -> s.tags().entrySet())
        .containsOnly(
            entry("rabbit.exchange", "test-exchange"),
            entry("rabbit.routing_key", "test.binding"),
            entry("rabbit.queue", "test-queue")
        );

    assertThat(testFixture.consumerSpans)
        .filteredOn(s -> s.kind() != CONSUMER)
        .flatExtracting(s -> s.tags().entrySet())
        .isEmpty();
  }

  // We will revisit this eventually, but these names mostly match the method names
  @Test public void method_names_as_span_names() throws Exception {
    testFixture.produceMessage();
    testFixture.awaitMessageConsumed();

    assertThat(testFixture.consumerSpans).hasSize(2);

    assertThat(testFixture.consumerSpans)
        .extracting(Span::name)
        .containsExactly("next-message", "on-message");
  }

  @Configuration
  public static class CommonRabbitConfig {
    @Bean
    public ConnectionFactory connectionFactory() {
      return new CachingConnectionFactory();
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
    public Tracing tracing(Reporter<Span> reporter) {
      return Tracing.newBuilder()
          .localServiceName("spring-amqp-producer")
          .sampler(Sampler.ALWAYS_SAMPLE)
          .spanReporter(reporter)
          .build();
    }

    @Bean
    public SpringRabbitTracing springRabbitTracing(Tracing tracing) {
      return SpringRabbitTracing.create(tracing);
    }

    @Bean
    public Reporter<Span> reporter(List<Span> spans) {
      return spans::add;
    }

    @Bean
    public List<Span> producerSpans() {
      return new ArrayList<>();
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
    public Tracing tracing(List<Span> spans) {
      return Tracing.newBuilder()
          .localServiceName("spring-amqp-consumer")
          .sampler(Sampler.ALWAYS_SAMPLE)
          .spanReporter(spans::add)
          .build();
    }

    @Bean
    public SpringRabbitTracing springRabbitTracing(Tracing tracing) {
      return SpringRabbitTracing.create(tracing);
    }

    @Bean
    public List<Span> consumerSpans() {
      return new ArrayList<>();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        SpringRabbitTracing springRabbitTracing
    ) {
      SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory =
          new SimpleRabbitListenerContainerFactory();
      simpleRabbitListenerContainerFactory.setConnectionFactory(connectionFactory);
      return springRabbitTracing.decorateSimpleRabbitListenerContainerFactory(
          simpleRabbitListenerContainerFactory
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

    private ApplicationContext producerContext;
    private ApplicationContext consumerContext;
    private List<Span> producerSpans;
    private List<Span> consumerSpans;

    ITSpringAmqpTracingTestFixture() {
      producerContext = producerSpringContext();
      consumerContext = consumerSpringContext();
      producerSpans = (List<Span>) producerContext.getBean("producerSpans");
      consumerSpans = (List<Span>) consumerContext.getBean("consumerSpans");
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
}
