package brave.spring.rabbit;

import brave.Tracing;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.MessagePostProcessor;
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
  final Field beforePublishPostProcessorsField;

  SpringRabbitTracing(Builder builder) {
    Tracing tracing = builder.tracing;
    String remoteServiceName = builder.remoteServiceName;
    this.tracingMessagePostProcessor = new TracingMessagePostProcessor(tracing, remoteServiceName);
    this.tracingRabbitListenerAdvice = new TracingRabbitListenerAdvice(tracing, remoteServiceName);
    Field beforePublishPostProcessorsField = null;
    try {
      beforePublishPostProcessorsField =
          RabbitTemplate.class.getDeclaredField("beforePublishPostProcessors");
      beforePublishPostProcessorsField.setAccessible(true);
    } catch (NoSuchFieldException e) {
    }
    this.beforePublishPostProcessorsField = beforePublishPostProcessorsField;
  }

  /** Creates an instrumented {@linkplain RabbitTemplate} */
  public RabbitTemplate newRabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setBeforePublishPostProcessors(tracingMessagePostProcessor);
    return rabbitTemplate;
  }

  /** Instruments an existing {@linkplain RabbitTemplate} */
  public RabbitTemplate decorateRabbitTemplate(RabbitTemplate rabbitTemplate) {
    // Skip out if we can't read the field for the existing post processors
    if (beforePublishPostProcessorsField == null) return rabbitTemplate;
    Collection<MessagePostProcessor> processors;
    try {
      processors = (Collection) beforePublishPostProcessorsField.get(rabbitTemplate);
    } catch (IllegalAccessException e) {
      return rabbitTemplate;
    }

    // If there are no existing post processors, return only the tracing one
    if (processors == null) {
      rabbitTemplate.setBeforePublishPostProcessors(tracingMessagePostProcessor);
      return rabbitTemplate;
    }

    // If there is an existing tracing post processor return
    for (MessagePostProcessor processor : processors) {
      if (processor instanceof TracingMessagePostProcessor) {
        return rabbitTemplate;
      }
    }

    // Otherwise, add ours and return
    List<MessagePostProcessor> newProcessors = new ArrayList<>(processors.size() + 1);
    newProcessors.addAll(processors);
    newProcessors.add(tracingMessagePostProcessor);
    rabbitTemplate.setBeforePublishPostProcessors(
        newProcessors.toArray(new MessagePostProcessor[0])
    );
    return rabbitTemplate;
  }

  /** Creates an instrumented {@linkplain SimpleRabbitListenerContainerFactory} */
  public SimpleRabbitListenerContainerFactory newSimpleRabbitListenerContainerFactory(
      ConnectionFactory connectionFactory
  ) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setAdviceChain(tracingRabbitListenerAdvice);
    return factory;
  }

  /** Instruments an existing {@linkplain SimpleRabbitListenerContainerFactory} */
  public SimpleRabbitListenerContainerFactory decorateSimpleRabbitListenerContainerFactory(
      SimpleRabbitListenerContainerFactory factory
  ) {
    Advice[] chain = factory.getAdviceChain();

    // If there are no existing advice, return only the tracing one
    if (chain == null) {
      factory.setAdviceChain(tracingRabbitListenerAdvice);
      return factory;
    }

    // If there is an existing tracing advice return
    for (Advice advice : chain) {
      if (advice instanceof TracingRabbitListenerAdvice) {
        return factory;
      }
    }

    // Otherwise, add ours and return
    Advice[] newChain = new Advice[chain.length + 1];
    System.arraycopy(chain, 0, newChain, 0, chain.length);
    newChain[chain.length] = tracingRabbitListenerAdvice;
    factory.setAdviceChain(newChain);
    return factory;
  }
}
