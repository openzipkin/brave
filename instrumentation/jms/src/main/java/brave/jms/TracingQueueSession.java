package brave.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;

final class TracingQueueSession extends TracingSession<QueueSession> implements QueueSession {
  static QueueSession create(QueueSession delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("queueSession == null");
    if (delegate instanceof TracingQueueSession) return delegate;
    return new TracingQueueSession(delegate, jmsTracing);
  }

  TracingQueueSession(QueueSession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public QueueReceiver createReceiver(Queue queue) throws JMSException {
    return TracingQueueReceiver.create(delegate.createReceiver(queue), jmsTracing);
  }

  @Override public QueueReceiver createReceiver(Queue queue, String messageSelector)
      throws JMSException {
    return TracingQueueReceiver.create(delegate.createReceiver(queue, messageSelector), jmsTracing);
  }

  @Override public QueueSender createSender(Queue queue) throws JMSException {
    return TracingQueueSender.create(delegate.createSender(queue), jmsTracing);
  }
}
