package brave.spring.rabbit;

import brave.Tracing;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Factory for Brave instrumented Spring Rabbit classes.
 */
public final class SpringRabbitTracing {

  private final Tracing tracing;
  private final TracingMessagePostProcessor tracingMessagePostProcessor;
  private final TracingRabbitListenerAdvice tracingRabbitListenerAdvice;

  public static SpringRabbitTracing create(Tracing tracing) {
    if (tracing == null) throw new IllegalArgumentException("tracing must not be null");
    return new SpringRabbitTracing(tracing);
  }

  private SpringRabbitTracing(Tracing tracing) {
    this.tracing = tracing;
    this.tracingMessagePostProcessor = new TracingMessagePostProcessor(tracing);
    this.tracingRabbitListenerAdvice = new TracingRabbitListenerAdvice(tracing);
  }

  /**
   * Creates an instrumented rabbit template.
   * @param connectionFactory
   * @return
   */
  public RabbitTemplate newRabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setBeforePublishPostProcessors(tracingMessagePostProcessor);
    return rabbitTemplate;
  }

  /**
   * Creates an instrumented SimpleRabbitListenerContainerFactory to be used to consume rabbit messages.
   * @param connectionFactory
   * @return
   */
  public SimpleRabbitListenerContainerFactory newSimpleMessageListenerContainerFactory(ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setAdviceChain(tracingRabbitListenerAdvice);
    return factory;
  }
}
