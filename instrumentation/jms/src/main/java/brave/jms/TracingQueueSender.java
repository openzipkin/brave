package brave.jms;

import brave.Span;
import brave.Tracer.SpanInScope;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueSender;

final class TracingQueueSender extends TracingMessageProducer<QueueSender> implements QueueSender {
  static QueueSender create(QueueSender delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("queueSender == null");
    if (delegate instanceof TracingQueueSender) return delegate;
    return new TracingQueueSender(delegate, jmsTracing);
  }

  TracingQueueSender(QueueSender delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public Queue getQueue() throws JMSException {
    return delegate.getQueue();
  }

  @Override public void send(Queue queue, Message message) throws JMSException {
    send(SendDestination.QUEUE, queue, message);
  }

  @Override
  public void send(Queue queue, Message message, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
    Span span = createAndStartProducerSpan(null, message);
    SpanInScope ws = tracer.withSpanInScope(span); // animal-sniffer mistakes this for AutoCloseable
    try {
      delegate.send(queue, message, deliveryMode, priority, timeToLive);
    } catch (RuntimeException | JMSException | Error e) {
      span.error(e);
      throw e;
    } finally {
      ws.close();
      span.finish();
    }
  }
}
