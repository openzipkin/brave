package brave.jms;

import brave.Span;
import brave.SpanCustomizer;
import brave.Tracer;
import brave.Tracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;

import static brave.jms.JmsTracing.JMS_DESTINATION;
import static brave.jms.JmsTracing.addB3SingleHeader;

final class TracingMessageConsumer implements MessageConsumer {
  final MessageConsumer delegate;
  final JmsTracing jmsTracing;
  final Tracing tracing;
  final Tracer tracer;
  final Extractor<Message> extractor;
  @Nullable final String remoteServiceName;

  TracingMessageConsumer(MessageConsumer delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    this.tracing = jmsTracing.tracing;
    this.tracer = tracing.tracer();
    this.extractor = jmsTracing.extractor;
    this.remoteServiceName = jmsTracing.remoteServiceName;
  }

  @Override public String getMessageSelector() throws JMSException {
    return delegate.getMessageSelector();
  }

  @Override public MessageListener getMessageListener() throws JMSException {
    return delegate.getMessageListener();
  }

  @Override public void setMessageListener(MessageListener listener) throws JMSException {
    if (!(listener instanceof TracingMessageListener)) {
      listener = new TracingMessageListener(listener, jmsTracing);
    }
    delegate.setMessageListener(listener);
  }

  @Override public Message receive() throws JMSException {
    Message message = delegate.receive();
    handleReceive(message);
    return message;
  }

  @Override public Message receive(long timeout) throws JMSException {
    Message message = delegate.receive(timeout);
    handleReceive(message);
    return message;
  }

  @Override public Message receiveNoWait() throws JMSException {
    Message message = delegate.receiveNoWait();
    handleReceive(message);
    return message;
  }

  @Override public void close() throws JMSException {
    delegate.close();
  }

  void handleReceive(Message message) {
    if (message == null || tracing.isNoop()) return;
    // remove prior propagation headers from the message
    TraceContextOrSamplingFlags extracted = jmsTracing.extractAndClearMessage(message);
    Span span = tracer.nextSpan(extracted);
    if (!span.isNoop()) {
      span.name("receive").kind(Span.Kind.CONSUMER);
      tagReceivedMessage(message, span.customizer());
      if (remoteServiceName != null) span.remoteServiceName(remoteServiceName);

      // incur timestamp overhead only once
      long timestamp = tracing.clock(span.context()).currentTimeMicroseconds();
      span.start(timestamp).finish(timestamp);
    }
    addB3SingleHeader(span.context(), message);
  }

  static void tagReceivedMessage(Message message, SpanCustomizer span) {
    try {
      Destination destination = message.getJMSDestination();
      if (destination != null) span.tag(JMS_DESTINATION, destination.toString());
    } catch (JMSException ignored) {
      // don't crash on wonky exceptions!
    }
  }
}
