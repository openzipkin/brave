package brave.spring.rabbit;

import brave.Tracing;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Factory for Brave instrumented Spring Rabbit classes.
 */
public final class SpringRabbitTracing {

  static final String
      RABBIT_EXCHANGE = "rabbit.exchange",
      RABBIT_ROUTING_KEY = "rabbit.routing_key",
      RABBIT_QUEUE = "rabbit.queue";

  public static SpringRabbitTracing create(Tracing tracing) {
    if (tracing == null) throw new NullPointerException("tracing == null");
    return new Builder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return new Builder(tracing);
  }

  public static final class Builder {
    final Tracing tracing;
    String remoteServiceName;

    Builder(Tracing tracing) {
      this.tracing = tracing;
    }

    /** The remote service name that describes the broker in the dependency graph. No default */
    public Builder remoteServiceName(String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
      return this;
    }

    public SpringRabbitTracing build() {
      return new SpringRabbitTracing(this);
    }
  }

  final TracingMessagePostProcessor tracingMessagePostProcessor;
  final TracingRabbitListenerAdvice tracingRabbitListenerAdvice;

  SpringRabbitTracing(Builder builder) {
    Tracing tracing = builder.tracing;
    String remoteServiceName = builder.remoteServiceName;
    this.tracingMessagePostProcessor = new TracingMessagePostProcessor(tracing, remoteServiceName);
    this.tracingRabbitListenerAdvice = new TracingRabbitListenerAdvice(tracing, remoteServiceName);
  }

  /** Creates an instrumented rabbit template. */
  public RabbitTemplate newRabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setBeforePublishPostProcessors(tracingMessagePostProcessor);
    return rabbitTemplate;
  }

  /**
   * Creates an instrumented SimpleRabbitListenerContainerFactory to be used to consume rabbit
   * messages.
   */
  public SimpleRabbitListenerContainerFactory newSimpleMessageListenerContainerFactory(
      ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setAdviceChain(tracingRabbitListenerAdvice);
    return factory;
  }
}
