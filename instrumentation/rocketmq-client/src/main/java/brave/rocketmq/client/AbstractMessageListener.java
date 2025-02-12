/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.messaging.MessagingRequest;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import org.apache.rocketmq.common.message.MessageExt;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static brave.Span.Kind.CONSUMER;
import static brave.internal.Throwables.propagateIfFatal;
import static brave.rocketmq.client.RocketMQTracing.ROCKETMQ_TOPIC;

/**
 * Read records headers to create and complete a child of the incoming
 * producers span if possible.
 * The spans are modeled as a duration 1 {@link Span.Kind#CONSUMER} span to represent consuming the
 * message from the rocketmq broker with a child span representing the processing of the message.
 */
abstract class AbstractMessageListener {
  final RocketMQTracing rocketMQTracing;
  final Tracing tracing;
  final Tracer tracer;
  final TraceContext.Extractor<MessageConsumerRequest> extractor;
  final SamplerFunction<MessagingRequest> sampler;
  @Nullable final String remoteServiceName;

  AbstractMessageListener(RocketMQTracing rocketMQTracing) {
    this.rocketMQTracing = rocketMQTracing;
    this.tracing = rocketMQTracing.messagingTracing.tracing();
    this.tracer = tracing.tracer();
    this.extractor = rocketMQTracing.consumerExtractor;
    this.sampler = rocketMQTracing.consumerSampler;
    this.remoteServiceName = rocketMQTracing.remoteServiceName;
  }

  <T> T processConsumeMessage(
      List<MessageExt> msgs,
      Function<List<MessageExt>, T> consumerFunc,
      BiFunction<T, T, Boolean> successFunc,
      T successStatus
  ) {
    for (MessageExt message : msgs) {
      MessageConsumerRequest request = new MessageConsumerRequest(message);
      TraceContextOrSamplingFlags extracted =
          rocketMQTracing.extractAndClearTraceIdHeaders(extractor, request, message.getProperties());
      Span consumerSpan = rocketMQTracing.nextMessagingSpan(sampler, request, extracted);
      Span listenerSpan = tracer.newChild(consumerSpan.context());

      if (!consumerSpan.isNoop()) {
        setConsumerSpan(consumerSpan, message.getTopic());
        // incur timestamp overhead only once
        long timestamp = tracing.clock(consumerSpan.context()).currentTimeMicroseconds();
        consumerSpan.start(timestamp);
        long consumerFinish = timestamp + 1L; // save a clock reading
        consumerSpan.finish(consumerFinish);
        // not using scoped span as we want to start with a pre-configured time
        listenerSpan.name("on-message").start(consumerFinish);
      }

      Tracer.SpanInScope scope = tracer.withSpanInScope(listenerSpan);
      Throwable error = null;
      T result;

      try {
        result = consumerFunc.apply(msgs);
      } catch (Throwable t) {
        propagateIfFatal(t);
        error = t;
        throw t;
      } finally {
        if (error != null) listenerSpan.error(error);
        listenerSpan.finish();
        scope.close();
      }

      if (!successFunc.apply(result, successStatus)) {
        return result;
      }
    }
    return successStatus;
  }

  void setConsumerSpan(Span span, String topic) {
    span.name("receive").kind(CONSUMER);
    span.tag(ROCKETMQ_TOPIC, topic);
    if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);
  }
}
