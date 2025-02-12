/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.rocketmq.client;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.consumer.listener.MessageListenerOrderly;
import org.apache.rocketmq.client.hook.SendMessageHook;

import java.util.Map;

/** Use this class to decorate your RocketMQ consumer / producer and enable Tracing. */
public final class RocketMQTracing {
  static final String
    ROCKETMQ_TOPIC = "rocketmq.topic",
    ROCKETMQ_TAGS = "rocketmq.tags";

  public static RocketMQTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return newBuilder(MessagingTracing.create(tracing));
  }

  /** @since 6.1 */
  public static RocketMQTracing create(MessagingTracing messagingTracing) {
    return newBuilder(messagingTracing).build();
  }

  /** @since 6.1 */
  public static Builder newBuilder(MessagingTracing messagingTracing) {
    return new Builder(messagingTracing);
  }

  public static final class Builder {
    final MessagingTracing messagingTracing;
    String remoteServiceName = "rocketmq";

    Builder(MessagingTracing messagingTracing) {
      if (messagingTracing == null) throw new NullPointerException("messagingTracing == null");
      this.messagingTracing = messagingTracing;
    }

    /** The remote service name that describes the broker in the dependency graph. Defaults to "rocketmq". */
    public Builder remoteServiceName(String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
      return this;
    }

    public RocketMQTracing build() {
      return new RocketMQTracing(this);
    }
  }

  final MessagingTracing messagingTracing;
  final Tracer tracer;
  final String remoteServiceName;
  final Extractor<MessageProducerRequest> producerExtractor;
  final Extractor<MessageConsumerRequest> consumerExtractor;
  final Injector<MessageProducerRequest> producerInjector;
  final Injector<MessageConsumerRequest> consumerInjector;
  final SamplerFunction<MessagingRequest> producerSampler, consumerSampler;
  final String[] traceIdHeaders;
  final TraceContextOrSamplingFlags emptyExtraction;

  RocketMQTracing(Builder builder) { // intentionally hidden constructor
    this.messagingTracing = builder.messagingTracing;
    this.tracer = builder.messagingTracing.tracing().tracer();
    this.remoteServiceName = builder.remoteServiceName;
    Propagation<String> propagation = messagingTracing.propagation();
    this.producerExtractor = propagation.extractor(MessageProducerRequest.GETTER);
    this.consumerExtractor = propagation.extractor(MessageConsumerRequest.GETTER);
    this.producerInjector = propagation.injector(MessageProducerRequest.SETTER);
    this.consumerInjector = propagation.injector(MessageConsumerRequest.SETTER);
    this.producerSampler = messagingTracing.producerSampler();
    this.consumerSampler = messagingTracing.consumerSampler();

    // We clear the trace ID headers, so that a stale consumer span is not preferred over current
    // listener. We intentionally don't clear BaggagePropagation.allKeyNames as doing so will
    // application fields "user_id" or "country_code"
    this.traceIdHeaders = propagation.keys().toArray(new String[0]);

    // When baggage or similar is in use, the result != TraceContextOrSamplingFlags.EMPTY
    this.emptyExtraction = propagation.extractor((c, k) -> null).extract(Boolean.TRUE);
  }

  <R> TraceContextOrSamplingFlags extractAndClearTraceIdHeaders(
    Extractor<R> extractor, R request, Map<String, String> properties
  ) {
    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    // Clear any propagation keys present in the headers
    if (extracted.samplingFlags() == null) { // then trace IDs were extracted
      if (properties != null) {
        clearTraceIdHeaders(properties);
      }
    }
    return extracted;
  }

  // We can't just skip clearing headers we use because we might inject B3 single, yet have stale B3
  // multi, or vice versa.
  void clearTraceIdHeaders(Map<String, String> headers) {
    for (String traceIDHeader : traceIdHeaders)
      headers.remove(traceIDHeader);
  }

  /** Creates a potential noop remote span representing this request */
  Span nextMessagingSpan(
    SamplerFunction<MessagingRequest> sampler,
    MessagingRequest request,
    TraceContextOrSamplingFlags extracted
  ) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the messaging sampler made a decision
    if (sampled == null && (sampled = sampler.trySample(request)) != null) {
      extracted = extracted.sampled(sampled);
    }
    return tracer.nextSpan(extracted);
  }

  /**
   * Implements a hook that creates a {@link Span.Kind#PRODUCER} span when a message is sent.
   */
  public SendMessageHook newSendMessageHook() {
    return new TracingSendMessageHook(this);
  }

  /**
   * Extracts or creates a {@link Span.Kind#CONSUMER} span for each message received. This span is
   * injected onto each message so it becomes the parent when a processor later calls {@link Tracer#nextSpan(TraceContextOrSamplingFlags)}.
   */
  public MessageListenerOrderly messageListenerOrderly(MessageListenerOrderly messageListenerOrderly) {
    return new TracingMessageListenerOrderly(this, messageListenerOrderly);
  }

  /**
   * Extracts or creates a {@link Span.Kind#CONSUMER} span for each message received. This span is
   * injected onto each message so it becomes the parent when a processor later calls {@link Tracer#nextSpan(TraceContextOrSamplingFlags)}.
   */
  public MessageListenerConcurrently messageListenerConcurrently(MessageListenerConcurrently messageListenerConcurrently) {
    return new TracingMessageListenerConcurrently(this, messageListenerConcurrently);
  }
}
