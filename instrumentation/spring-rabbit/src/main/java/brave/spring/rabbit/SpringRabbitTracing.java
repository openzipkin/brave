package brave.spring.rabbit;

import brave.Tracing;
import brave.propagation.B3SingleFormat;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static brave.spring.rabbit.SpringRabbitPropagation.B3_SINGLE_TEST_HEADERS;
import static brave.spring.rabbit.SpringRabbitPropagation.TEST_CONTEXT;

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
    String remoteServiceName = "rabbitmq";
    boolean writeB3SingleFormat;

    Builder(Tracing tracing) {
      this.tracing = tracing;
    }

    /**
     * The remote service name that describes the broker in the dependency graph. Defaults to
     * "rabbitmq"
     */
    public Builder remoteServiceName(String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
      return this;
    }

    /**
     * When true, only writes a single {@link B3SingleFormat b3 header} for outbound propagation.
     *
     * <p>Use this to reduce overhead. Note: normal {@link Tracing#propagation()} is used to parse
     * incoming headers. The implementation must be able to read "b3" headers.
     */
    public Builder writeB3SingleFormat(boolean writeB3SingleFormat) {
      this.writeB3SingleFormat = writeB3SingleFormat;
      return this;
    }

    public SpringRabbitTracing build() {
      return new SpringRabbitTracing(this);
    }
  }

  final Tracing tracing;
  final Extractor<MessageProperties> extractor;
  final Injector<MessageProperties> injector;
  final List<String> propagationKeys;
  final String remoteServiceName;
  final Field beforePublishPostProcessorsField;

  SpringRabbitTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.tracing;
    this.extractor = tracing.propagation().extractor(SpringRabbitPropagation.GETTER);
    List<String> keyList = builder.tracing.propagation().keys();
    // Use a more efficient injector if we are only propagating a single header
    if (builder.writeB3SingleFormat || keyList.equals(Propagation.B3_SINGLE_STRING.keys())) {
      TraceContext testExtraction = extractor.extract(B3_SINGLE_TEST_HEADERS).context();
      if (!TEST_CONTEXT.equals(testExtraction)) {
        throw new IllegalArgumentException(
            "SpringRabbitTracing.Builder.writeB3SingleFormat set, but Tracing.Builder.propagationFactory cannot parse this format!");
      }
      this.injector = SpringRabbitPropagation.B3_SINGLE_INJECTOR;
    } else {
      this.injector = tracing.propagation().injector(SpringRabbitPropagation.SETTER);
    }
    this.propagationKeys = keyList;
    this.remoteServiceName = builder.remoteServiceName;
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
    TracingMessagePostProcessor tracingMessagePostProcessor = new TracingMessagePostProcessor(this);
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

    TracingMessagePostProcessor tracingMessagePostProcessor = new TracingMessagePostProcessor(this);
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
    factory.setAdviceChain(new TracingRabbitListenerAdvice(this));
    return factory;
  }

  /** Instruments an existing {@linkplain SimpleRabbitListenerContainerFactory} */
  public SimpleRabbitListenerContainerFactory decorateSimpleRabbitListenerContainerFactory(
      SimpleRabbitListenerContainerFactory factory
  ) {
    Advice[] chain = factory.getAdviceChain();

    TracingRabbitListenerAdvice tracingAdvice = new TracingRabbitListenerAdvice(this);
    // If there are no existing advice, return only the tracing one
    if (chain == null) {
      factory.setAdviceChain(tracingAdvice);
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
    newChain[chain.length] = tracingAdvice;
    factory.setAdviceChain(newChain);
    return factory;
  }

  TraceContextOrSamplingFlags extractAndClearHeaders(Message message) {
    MessageProperties messageProperties = message.getMessageProperties();
    TraceContextOrSamplingFlags extracted = extractor.extract(messageProperties);
    Map<String, Object> headers = messageProperties.getHeaders();
    clearHeaders(headers);
    return extracted;
  }

  void clearHeaders(Map<String, Object> headers) {
    for (int i = 0, length = propagationKeys.size(); i < length; i++) {
      headers.remove(propagationKeys.get(i));
    }
  }
}
