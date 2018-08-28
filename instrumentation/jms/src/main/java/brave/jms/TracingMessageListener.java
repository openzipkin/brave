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
 * The spans are modeled as a duration 1 {@link Span.Kind#CONSUMER} span to represent receiving
 * message from the JMS destination, and a child span representing the processing of the message.
 */
final class TracingMessageListener implements MessageListener {

  static MessageListener create(MessageListener delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingMessageListener) return delegate;
    return new TracingMessageListener(delegate, jmsTracing);
  }

  final MessageListener delegate;
  final JmsTracing jmsTracing;
  final Tracing tracing;
  final Tracer tracer;
  final String remoteServiceName;

  TracingMessageListener(MessageListener delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    this.tracing = jmsTracing.tracing;
    this.tracer = jmsTracing.tracing.tracer();
    this.remoteServiceName = jmsTracing.remoteServiceName;
  }

  @Override public void onMessage(Message message) {
    TraceContextOrSamplingFlags extracted = jmsTracing.extractAndClearMessage(message);

    // JMS has no visibility of the incoming message, which incidentally could be local!
    Span consumerSpan = tracer.nextSpan(extracted).kind(CONSUMER).name("receive");
    Span listenerSpan = tracer.newChild(consumerSpan.context()).name("on-message");

    if (!consumerSpan.isNoop()) {
      long timestamp = tracing.clock(consumerSpan.context()).currentTimeMicroseconds();
      consumerSpan.start(timestamp);
      if (remoteServiceName != null) consumerSpan.remoteServiceName(remoteServiceName);
      jmsTracing.tagQueueOrTopic(message, consumerSpan);
      long consumerFinish = timestamp + 1L; // save a clock reading
      consumerSpan.finish(consumerFinish);
      listenerSpan.start(consumerFinish); // not using scoped span as we want to start late
    }

    try (SpanInScope ws = tracer.withSpanInScope(listenerSpan)) {
      delegate.onMessage(message);
    } catch (Throwable t) {
      listenerSpan.error(t);
      throw t;
    } finally {
      listenerSpan.finish();
    }
  }
}
