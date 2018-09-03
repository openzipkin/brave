package brave.jms;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.jms.Message;
import javax.jms.MessageListener;

import static brave.Span.Kind.CONSUMER;

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
    if (delegate instanceof TracingMessageListener) return delegate;
    return new TracingMessageListener(delegate, jmsTracing, true);
  }

  final MessageListener delegate;
  final JmsTracing jmsTracing;
  final Tracing tracing;
  final Tracer tracer;
  final String remoteServiceName;
  final boolean addConsumerSpan;

  TracingMessageListener(MessageListener delegate, JmsTracing jmsTracing, boolean addConsumerSpan) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    this.tracing = jmsTracing.tracing;
    this.tracer = jmsTracing.tracing.tracer();
    this.remoteServiceName = jmsTracing.remoteServiceName;
    this.addConsumerSpan = addConsumerSpan;
  }

  @Override public void onMessage(Message message) {
    Span listenerSpan = startMessageListenerSpan(message);
    try (SpanInScope ws = tracer.withSpanInScope(listenerSpan)) {
      delegate.onMessage(message);
    } catch (Throwable t) {
      listenerSpan.error(t);
      throw t;
    } finally {
      listenerSpan.finish();
    }
  }

  Span startMessageListenerSpan(Message message) {
    if (!addConsumerSpan) return jmsTracing.nextSpan(message).name("on-message").start();
    TraceContextOrSamplingFlags extracted = jmsTracing.extractAndClearMessage(message);

    // JMS has no visibility of the incoming message, which incidentally could be local!
    Span consumerSpan = tracer.nextSpan(extracted).kind(CONSUMER).name("receive");
    Span listenerSpan = tracer.newChild(consumerSpan.context());

    if (!consumerSpan.isNoop()) {
      long timestamp = tracing.clock(consumerSpan.context()).currentTimeMicroseconds();
      consumerSpan.start(timestamp);
      if (remoteServiceName != null) consumerSpan.remoteServiceName(remoteServiceName);
      jmsTracing.tagQueueOrTopic(message, consumerSpan);
      long consumerFinish = timestamp + 1L; // save a clock reading
      consumerSpan.finish(consumerFinish);

      // not using scoped span as we want to start late
      listenerSpan.name("on-message").start(consumerFinish);
    }
    return listenerSpan;
  }
}
