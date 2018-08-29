package brave.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;

final class TracingQueueSession extends TracingSession implements QueueSession {
  static QueueSession create(QueueSession delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("queueSession == null");
    if (delegate instanceof TracingQueueSession) return delegate;
    return new TracingQueueSession(delegate, jmsTracing);
  }

  final QueueSession qs;

  TracingQueueSession(QueueSession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    qs = delegate;
  }

  @Override public QueueReceiver createReceiver(Queue queue) throws JMSException {
    return TracingQueueReceiver.create(qs.createReceiver(queue), jmsTracing);
  }

  @Override public QueueReceiver createReceiver(Queue queue, String messageSelector)
      throws JMSException {
    return TracingQueueReceiver.create(qs.createReceiver(queue, messageSelector), jmsTracing);
  }

  @Override public QueueSender createSender(Queue queue) throws JMSException {
    return TracingQueueSender.create(qs.createSender(queue), jmsTracing);
  }
}
