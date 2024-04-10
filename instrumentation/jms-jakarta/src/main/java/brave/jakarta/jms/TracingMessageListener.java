/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.messaging.MessagingRequest;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import static brave.Span.Kind.CONSUMER;
import static brave.internal.Throwables.propagateIfFatal;
import static brave.jakarta.jms.MessageParser.destination;

/**
 * When {@link #addConsumerSpan} this creates 2 spans:
 * <ol>
 *   <li>A duration 1 {@link Span.Kind#CONSUMER} span to represent receipt from the destination</li>
 *   <li>A child span with the duration of the delegated listener</li>
 * </ol>
 *
 * <p>{@link #addConsumerSpan} should only be set when the message consumer is not traced.
 */
final class TracingMessageListener implements MessageListener {
  /** Creates a message listener which also adds a consumer span. */
  static MessageListener create(MessageListener delegate, JmsTracing jmsTracing) {
    if (delegate == null) return null;
    if (delegate instanceof TracingMessageListener) return delegate;
    return new TracingMessageListener(delegate, jmsTracing, true);
  }

  final MessageListener delegate;
  final JmsTracing jmsTracing;
  final Tracing tracing;
  final Tracer tracer;
  final Extractor<MessageConsumerRequest> extractor;
  final SamplerFunction<MessagingRequest> sampler;
  final String remoteServiceName;
  final boolean addConsumerSpan;

  TracingMessageListener(MessageListener delegate, JmsTracing jmsTracing, boolean addConsumerSpan) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    this.tracing = jmsTracing.tracing;
    this.tracer = jmsTracing.tracer;
    this.extractor = jmsTracing.messageConsumerExtractor;
    this.sampler = jmsTracing.consumerSampler;
    this.remoteServiceName = jmsTracing.remoteServiceName;
    this.addConsumerSpan = addConsumerSpan;
  }

  @Override public void onMessage(Message message) {
    Span listenerSpan = startMessageListenerSpan(message);
    SpanInScope scope = tracer.withSpanInScope(listenerSpan);
    Throwable error = null;
    try {
      delegate.onMessage(message);
    } catch (Throwable t) {
      propagateIfFatal(t);
      error = t;
      throw t;
    } finally {
      if (error != null) listenerSpan.error(error);
      listenerSpan.finish();
      scope.close();
    }
  }

  Span startMessageListenerSpan(Message message) {
    if (!addConsumerSpan) return jmsTracing.nextSpan(message).name("on-message").start();

    MessageConsumerRequest request = new MessageConsumerRequest(message, destination(message));

    TraceContextOrSamplingFlags extracted =
      jmsTracing.extractAndClearTraceIdProperties(extractor, request, message);
    Span consumerSpan = jmsTracing.nextMessagingSpan(sampler, request, extracted);

    // JMS has no visibility of the incoming message, which incidentally could be local!
    consumerSpan.kind(CONSUMER).name("receive");
    Span listenerSpan = tracer.newChild(consumerSpan.context());

    if (!consumerSpan.isNoop()) {
      long timestamp = tracing.clock(consumerSpan.context()).currentTimeMicroseconds();
      consumerSpan.start(timestamp);
      if (remoteServiceName != null) consumerSpan.remoteServiceName(remoteServiceName);
      jmsTracing.tagQueueOrTopic(request, consumerSpan);
      long consumerFinish = timestamp + 1L; // save a clock reading
      consumerSpan.finish(consumerFinish);

      // not using scoped span as we want to start late
      listenerSpan.name("on-message").start(consumerFinish);
    }
    return listenerSpan;
  }
}
