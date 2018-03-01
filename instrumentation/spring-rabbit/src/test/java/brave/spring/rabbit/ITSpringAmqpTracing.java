package brave.spring.rabbit;

import brave.Tracing;
import brave.sampler.Sampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static zipkin2.Span.Kind.CONSUMER;
import static zipkin2.Span.Kind.SERVER;

public class ITSpringAmqpTracing {

  @Rule public TestName testName = new TestName();

  @ClassRule public static BrokerRunning brokerRunning = BrokerRunning.isRunning();

  @Test public void propagates_trace_info_across_amqp_from_producer() throws Exception {
    ApplicationContext producerContext = producerSpringContext();
    ApplicationContext consumerContext = consumerSpringContext();

    produceMessage(producerContext);
    awaitMessageConsumed(consumerContext);

    List<Span> producerSpans = (List<Span>) producerContext.getBean("producerSpans");
    List<Span> consumerSpans = (List<Span>) consumerContext.getBean("consumerSpans");

    assertThat(producerSpans).hasSize(1);
    assertThat(consumerSpans).hasSize(2);

    String originatingTraceId = producerSpans.get(0).traceId();
    String consumerSpanId = consumerSpans.get(0).id();

    assertThat(consumerSpans)
        .extracting(Span::kind, Span::traceId, Span::parentId)
        .containsExactly(
            tuple(CONSUMER, originatingTraceId, originatingTraceId),
            tuple(SERVER, originatingTraceId, consumerSpanId)
        );
  }

  @Test public void clears_message_headers_after_propagation() throws Exception {
    ApplicationContext producerContext = producerSpringContext();
    ApplicationContext consumerContext = consumerSpringContext();

    produceMessage(producerContext);
    awaitMessageConsumed(consumerContext);

    HelloWorldRabbitConsumer consumer = consumerContext.getBean(HelloWorldRabbitConsumer.class);

    Message capturedMessage = consumer.capturedMessage;
    Map<String, Object> headers = capturedMessage.getMessageProperties().getHeaders();
    assertThat(headers.keySet()).containsExactly("not-zipkin-header");
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

  private void produceMessage(ApplicationContext producerContext) {
    HelloWorldRabbitProducer rabbitProducer =
        producerContext.getBean(HelloWorldRabbitProducer.class);
    rabbitProducer.send();
  }

  private void awaitMessageConsumed(ApplicationContext consumerContext)
      throws InterruptedException {
    CountDownLatch messageReceivedLatch = consumerContext.getBean(CountDownLatch.class);
    messageReceivedLatch.await();
  }

  @Configuration
  public static class CommonRabbitConfig {
    @Bean
    public ConnectionFactory connectionFactory() {
      return new CachingConnectionFactory();
    }

    @Bean
    public Exchange exchange() {
      return ExchangeBuilder.fanoutExchange("test-exchange").build();
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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
        SpringRabbitTracing springRabbitTracing) {
      RabbitTemplate rabbitTemplate = springRabbitTracing.newRabbitTemplate(connectionFactory);
      rabbitTemplate.setExchange("test-exchange");
      return rabbitTemplate;
    }

    @Bean
    public HelloWorldRabbitProducer tracingRabbitProducer(RabbitTemplate rabbitTemplate) {
      return new HelloWorldRabbitProducer(rabbitTemplate);
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
        SpringRabbitTracing springRabbitTracing) {
      return springRabbitTracing.newSimpleMessageListenerContainerFactory(connectionFactory);
    }

    @Bean
    public HelloWorldRabbitConsumer helloWorldRabbitConsumer(CountDownLatch countDownLatch) {
      return new HelloWorldRabbitConsumer(countDownLatch);
    }

    @Bean CountDownLatch messageReceivedLatch() {
      return new CountDownLatch(1);
    }
  }

  private static class HelloWorldRabbitProducer {
    private final RabbitTemplate rabbitTemplate;

    HelloWorldRabbitProducer(RabbitTemplate rabbitTemplate) {
      this.rabbitTemplate = rabbitTemplate;
    }

    void send() {
      byte[] messageBody = "hello world".getBytes();
      MessageProperties properties = new MessageProperties();
      properties.setHeader("not-zipkin-header", "fakeValue");
      Message message = MessageBuilder.withBody(messageBody).andProperties(properties).build();
      rabbitTemplate.send(message);
    }
  }

  private static class HelloWorldRabbitConsumer {
    private final CountDownLatch countDownLatch;
    private Message capturedMessage;

    HelloWorldRabbitConsumer(CountDownLatch countDownLatch) {
      this.countDownLatch = countDownLatch;
    }

    @RabbitListener(queues = "test-queue")
    public void receive(Message message) {
      this.capturedMessage = message;
      this.countDownLatch.countDown();
    }
  }
}
