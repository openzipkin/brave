/*
 * Copyright 2013-2020 The OpenZipkin Authors
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

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.propagation.B3Propagation;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
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
    return newBuilder(tracing).build();
  }

  /** @since 5.9 */
  public static SpringRabbitTracing create(MessagingTracing messagingTracing) {
    return newBuilder(messagingTracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return newBuilder(MessagingTracing.create(tracing));
  }

  /** @since 5.9 */
  public static Builder newBuilder(MessagingTracing messagingTracing) {
    return new Builder(messagingTracing);
  }

  public static final class Builder {
    final MessagingTracing messagingTracing;
    String remoteServiceName = "rabbitmq";

    Builder(MessagingTracing messagingTracing) {
      if (messagingTracing == null) throw new NullPointerException("messagingTracing == null");
      this.messagingTracing = messagingTracing;
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
     * @deprecated as of v5.9, this is ignored because single format is default for messaging. Use
     * {@link B3Propagation#newFactoryBuilder()} to change the default.
     */
    @Deprecated public Builder writeB3SingleFormat(boolean writeB3SingleFormat) {
      return this;
    }

    public SpringRabbitTracing build() {
      return new SpringRabbitTracing(this);
    }
  }

  final Tracing tracing;
  final Tracer tracer;
  final Extractor<MessageProducerRequest> producerExtractor;
  final Extractor<MessageConsumerRequest> consumerExtractor;
  final Injector<MessageProducerRequest> producerInjector;
  final Injector<MessageConsumerRequest> consumerInjector;
  final String[] traceIdHeaders;
  final SamplerFunction<MessagingRequest> producerSampler, consumerSampler;
  final String remoteServiceName;

  @Nullable final Field beforePublishPostProcessorsField;
  @Nullable final Field beforeSendReplyPostProcessorsField;

  SpringRabbitTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.messagingTracing.tracing();
    this.tracer = tracing.tracer();
    MessagingTracing messagingTracing = builder.messagingTracing;
    Propagation<String> propagation = messagingTracing.propagation();
    this.producerExtractor = propagation.extractor(MessageProducerRequest.GETTER);
    this.consumerExtractor = propagation.extractor(MessageConsumerRequest.GETTER);
    this.producerInjector = propagation.injector(MessageProducerRequest.SETTER);
    this.consumerInjector = propagation.injector(MessageConsumerRequest.SETTER);
    this.producerSampler = messagingTracing.producerSampler();
    this.consumerSampler = messagingTracing.consumerSampler();
    this.remoteServiceName = builder.remoteServiceName;

    // We clear the trace ID headers, so that a stale consumer span is not preferred over current
    // listener. We intentionally don't clear BaggagePropagation.allKeyNames as doing so will
    // application fields "user_id" or "country_code"
    this.traceIdHeaders = propagation.keys().toArray(new String[0]);

    beforePublishPostProcessorsField =
      getField(RabbitTemplate.class, "beforePublishPostProcessors");
    beforeSendReplyPostProcessorsField =
      getField(AbstractRabbitListenerContainerFactory.class, "beforeSendReplyPostProcessors");
  }

  /** Allows us to work around lack of an append command. */
  @Nullable static Field getField(Class<?> clazz, String name) {
    Field result = null;
    try {
      result = clazz.getDeclaredField(name);
      result.setAccessible(true);
    } catch (NoSuchFieldException e) {
    }
    return result;
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
    MessagePostProcessor[] beforePublishPostProcessors =
      appendTracingMessagePostProcessor(rabbitTemplate, beforePublishPostProcessorsField);
    if (beforePublishPostProcessors != null) {
      rabbitTemplate.setBeforePublishPostProcessors(beforePublishPostProcessors);
    }
    return rabbitTemplate;
  }

  /** Creates an instrumented {@linkplain SimpleRabbitListenerContainerFactory} */
  public SimpleRabbitListenerContainerFactory newSimpleRabbitListenerContainerFactory(
    ConnectionFactory connectionFactory
  ) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setAdviceChain(new TracingRabbitListenerAdvice(this));
    factory.setBeforeSendReplyPostProcessors(new TracingMessagePostProcessor(this));
    return factory;
  }

  /** Instruments an existing {@linkplain SimpleRabbitListenerContainerFactory} */
  public SimpleRabbitListenerContainerFactory decorateSimpleRabbitListenerContainerFactory(
    SimpleRabbitListenerContainerFactory factory
  ) {
    Advice[] advice = prependTracingRabbitListenerAdvice(factory);
    if (advice != null) factory.setAdviceChain(advice);

    MessagePostProcessor[] beforeSendReplyPostProcessors =
      appendTracingMessagePostProcessor(factory, beforeSendReplyPostProcessorsField);
    if (beforeSendReplyPostProcessors != null) {
      factory.setBeforeSendReplyPostProcessors(beforeSendReplyPostProcessors);
    }

    return factory;
  }

  <R> TraceContextOrSamplingFlags extractAndClearTraceIdHeaders(
    Extractor<R> extractor, R request, Message message
  ) {
    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    // Clear any propagation keys present in the headers
    if (extracted.samplingFlags() == null) { // then trace IDs were extracted
      MessageProperties properties = message.getMessageProperties();
      if (properties != null) clearTraceIdHeaders(properties.getHeaders());
    }
    return extracted;
  }

  /** Creates a potentially noop remote span representing this request */
  Span nextMessagingSpan(
    SamplerFunction<MessagingRequest> sampler,
    MessagingRequest request,
    TraceContextOrSamplingFlags extracted
  ) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the messaging sampler made a decision
    if (sampled == null && (sampled = sampler.trySample(request)) != null) {
      extracted = extracted.sampled(sampled.booleanValue());
    }
    return tracer.nextSpan(extracted);
  }

  // We can't just skip clearing headers we use because we might inject B3 single, yet have stale B3
  // multi, or visa versa.
  void clearTraceIdHeaders(Map<String, Object> headers) {
    for (String traceIDHeader : traceIdHeaders) headers.remove(traceIDHeader);
  }

  /** Returns {@code null} if a change was impossible or not needed */
  @Nullable MessagePostProcessor[] appendTracingMessagePostProcessor(Object obj, Field field) {
    // Skip out if we can't read the field for the existing post processors
    if (field == null) return null;
    MessagePostProcessor[] processors;
    try {
      // don't use "field.get(obj) instanceof X" as the field could be null
      if (Collection.class.isAssignableFrom(field.getType())) {
        Collection<MessagePostProcessor> collection =
          (Collection<MessagePostProcessor>) field.get(obj);
        processors = collection != null ? collection.toArray(new MessagePostProcessor[0]) : null;
      } else if (MessagePostProcessor[].class.isAssignableFrom(field.getType())) {
        processors = (MessagePostProcessor[]) field.get(obj);
      } else { // unusable field value
        return null;
      }
    } catch (Exception e) {
      return null; // reflection error or collection element mismatch
    }

    TracingMessagePostProcessor tracingMessagePostProcessor = new TracingMessagePostProcessor(this);
    // If there are no existing post processors, return only the tracing one
    if (processors == null) {
      return new MessagePostProcessor[] {tracingMessagePostProcessor};
    }

    // If there is an existing tracing post processor return
    for (MessagePostProcessor processor : processors) {
      if (processor instanceof TracingMessagePostProcessor) {
        return null;
      }
    }

    // Otherwise, append ours and return
    MessagePostProcessor[] result = new MessagePostProcessor[processors.length + 1];
    System.arraycopy(processors, 0, result, 0, processors.length);
    result[processors.length] = tracingMessagePostProcessor;
    return result;
  }

  /** Returns {@code null} if a change was impossible or not needed */
  @Nullable
  Advice[] prependTracingRabbitListenerAdvice(SimpleRabbitListenerContainerFactory factory) {
    Advice[] chain = factory.getAdviceChain();

    TracingRabbitListenerAdvice tracingAdvice = new TracingRabbitListenerAdvice(this);
    // If there are no existing advice, return only the tracing one
    if (chain == null) return new Advice[] {tracingAdvice};

    // If there is an existing tracing advice return
    for (Advice advice : chain) {
      if (advice instanceof TracingRabbitListenerAdvice) {
        return null;
      }
    }

    // Otherwise, prepend ours and return
    Advice[] result = new Advice[chain.length + 1];
    result[0] = tracingAdvice;
    System.arraycopy(chain, 0, result, 1, chain.length);
    return result;
  }
}
