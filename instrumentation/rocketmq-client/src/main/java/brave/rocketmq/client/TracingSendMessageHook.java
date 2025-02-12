/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import brave.Span;
import brave.Tracer;
import brave.internal.Nullable;
import brave.messaging.MessagingRequest;
import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

import static brave.Span.Kind.PRODUCER;
import static brave.rocketmq.client.RocketMQTracing.ROCKETMQ_TAGS;
import static brave.rocketmq.client.RocketMQTracing.ROCKETMQ_TOPIC;

/**
 * For the user side, there are many overloaded methods for send message in
 * {@link org.apache.rocketmq.client.producer.MQProducer}, so implementing
 * {@link org.apache.rocketmq.client.hook.SendMessageHook} might be an efficient approach to enable tracing.
 */
final class TracingSendMessageHook implements SendMessageHook {
  final RocketMQTracing rocketMQTracing;
  final CurrentTraceContext currentTraceContext;
  final Tracer tracer;
  final TraceContext.Extractor<MessageProducerRequest> extractor;
  final SamplerFunction<MessagingRequest> sampler;
  final TraceContext.Injector<MessageProducerRequest> injector;
  @Nullable final String remoteServiceName;

  TracingSendMessageHook(RocketMQTracing rocketMQTracing) {
    this.rocketMQTracing = rocketMQTracing;
    this.currentTraceContext = rocketMQTracing.messagingTracing.tracing().currentTraceContext();
    this.tracer = rocketMQTracing.messagingTracing.tracing().tracer();
    this.extractor = rocketMQTracing.producerExtractor;
    this.sampler = rocketMQTracing.producerSampler;
    this.injector = rocketMQTracing.producerInjector;
    this.remoteServiceName = rocketMQTracing.remoteServiceName;
  }

  @Override public String hookName() {
    return "brave-tracing-producer";
  }

  @Override public void sendMessageBefore(SendMessageContext context) {
    if (context == null || context.getMessage() == null) {
      return;
    }
    Message message = context.getMessage();
    MessageProducerRequest request = new MessageProducerRequest(message);
    TraceContext maybeParent = currentTraceContext.get();
    // Unlike message consumers, we try current span before trying extraction. This is the proper
    // order because the span in scope should take precedence over a potentially stale header entry.
    //
    // NOTE: Brave instrumentation used properly does not result in stale header entries, as we
    // always clear message headers after reading.
    Span span;
    if (maybeParent == null) {
      TraceContextOrSamplingFlags extracted =
        rocketMQTracing.extractAndClearTraceIdHeaders(extractor, request, message.getProperties());
      span = rocketMQTracing.nextMessagingSpan(sampler, request, extracted);
    } else {
      span = tracer.newChild(maybeParent);
    }

    if (!span.isNoop()) {
      span.kind(PRODUCER).name("send");
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
      if (message.getTags() != null && !"".equals(message.getTags())) {
        span.tag(ROCKETMQ_TAGS, message.getTags());
      }
      span.tag(ROCKETMQ_TOPIC, message.getTopic());
      span.start();
    }

    context.setMqTraceContext(span);
    injector.inject(span.context(), request);
  }

  @Override public void sendMessageAfter(SendMessageContext context) {
    if (context == null || context.getMessage() == null || context.getMqTraceContext() == null) {
      return;
    }

    SendResult sendResult = context.getSendResult();
    Span span = (Span) context.getMqTraceContext();
    MessageProducerRequest request = new MessageProducerRequest(context.getMessage());
    if (sendResult == null) {
      if (context.getCommunicationMode() == CommunicationMode.ASYNC) {
        return;
      }
      span.finish();
      injector.inject(span.context(), request);
      return;
    }

    injector.inject(span.context(), request);
    span.finish();
  }
}
