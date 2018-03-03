package brave.spring.rabbit;

import brave.Tracing;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Factory for Brave instrumented Spring Rabbit classes.
 */
public final class SpringRabbitTracing {
  public static SpringRabbitTracing create(Tracing tracing) {
    if (tracing == null) throw new NullPointerException("tracing == null");
    return new SpringRabbitTracing(tracing);
  }

  final TracingMessagePostProcessor tracingMessagePostProcessor;
  final TracingRabbitListenerAdvice tracingRabbitListenerAdvice;

  SpringRabbitTracing(Tracing tracing) {
    this.tracingMessagePostProcessor = new TracingMessagePostProcessor(tracing);
    this.tracingRabbitListenerAdvice = new TracingRabbitListenerAdvice(tracing);
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
