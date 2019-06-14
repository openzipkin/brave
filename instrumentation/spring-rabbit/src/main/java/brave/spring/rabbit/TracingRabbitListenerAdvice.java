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

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContextOrSamplingFlags;
import com.rabbitmq.client.Channel;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import static brave.Span.Kind.CONSUMER;
import static brave.spring.rabbit.SpringRabbitTracing.RABBIT_EXCHANGE;
import static brave.spring.rabbit.SpringRabbitTracing.RABBIT_QUEUE;
import static brave.spring.rabbit.SpringRabbitTracing.RABBIT_ROUTING_KEY;

/**
 * TracingRabbitListenerAdvice is an AOP advice to be used with the {@link
 * SimpleMessageListenerContainer#setAdviceChain(Advice...) SimpleMessageListenerContainer} in order
 * to add tracing functionality to a spring-rabbit managed message consumer. While a majority of
 * brave's instrumentation points is implemented using a delegating wrapper approach, this extension
 * point was ideal as it covers both programmatic and {@link RabbitListener} annotation driven amqp
 * consumers.
 *
 * The spans are modeled as a duration 1 {@link Span.Kind#CONSUMER} span to represent consuming the
 * message from the rabbit broker with a child span representing the processing of the message.
 */
final class TracingRabbitListenerAdvice implements MethodInterceptor {

  final SpringRabbitTracing springRabbitTracing;
  final Tracing tracing;
  final Tracer tracer;
  @Nullable final String remoteServiceName;

  TracingRabbitListenerAdvice(SpringRabbitTracing springRabbitTracing) {
    this.springRabbitTracing = springRabbitTracing;
    this.tracing = springRabbitTracing.tracing;
    this.tracer = tracing.tracer();
    this.remoteServiceName = springRabbitTracing.remoteServiceName;
  }

  /**
   * MethodInterceptor for {@link SimpleMessageListenerContainer.ContainerDelegate#invokeListener(Channel,
   * Message)}
   */
  @Override public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    Message message = (Message) methodInvocation.getArguments()[1];
    TraceContextOrSamplingFlags extracted = springRabbitTracing.extractAndClearHeaders(message);

    // named for BlockingQueueConsumer.nextMessage, which we can't currently see
    Span consumerSpan = tracer.nextSpan(extracted);
    Span listenerSpan = tracer.newChild(consumerSpan.context());

    if (!consumerSpan.isNoop()) {
      setConsumerSpan(consumerSpan, message.getMessageProperties());

      // incur timestamp overhead only once
      long timestamp = tracing.clock(consumerSpan.context()).currentTimeMicroseconds();
      consumerSpan.start(timestamp);
      long consumerFinish = timestamp + 1L; // save a clock reading
      consumerSpan.finish(consumerFinish);

      // not using scoped span as we want to start with a pre-configured time
      listenerSpan.name("on-message").start(consumerFinish);
    }

    try (SpanInScope ws = tracer.withSpanInScope(listenerSpan)) {
      return methodInvocation.proceed();
    } catch (Throwable t) {
      listenerSpan.error(t);
      throw t;
    } finally {
      listenerSpan.finish();
    }
  }

  void setConsumerSpan(Span span, MessageProperties properties) {
    span.name("next-message").kind(CONSUMER);
    maybeTag(span, RABBIT_EXCHANGE, properties.getReceivedExchange());
    maybeTag(span, RABBIT_ROUTING_KEY, properties.getReceivedRoutingKey());
    maybeTag(span, RABBIT_QUEUE, properties.getConsumerQueue());
    if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
  }

  static void maybeTag(Span span, String tag, String value) {
    if (value != null) span.tag(tag, value);
  }
}
