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

  TracingQueueSession(QueueSession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public QueueReceiver createReceiver(Queue queue) throws JMSException {
    QueueReceiver qr = ((QueueSession) delegate).createReceiver(queue);
    return TracingQueueReceiver.create(qr, jmsTracing);
  }

  @Override public QueueReceiver createReceiver(Queue queue, String messageSelector)
      throws JMSException {
    QueueReceiver qr = ((QueueSession) delegate).createReceiver(queue, messageSelector);
    return TracingQueueReceiver.create(qr, jmsTracing);
  }

  @Override public QueueSender createSender(Queue queue) throws JMSException {
    QueueSender qs = ((QueueSession) delegate).createSender(queue);
    return TracingQueueSender.create(qs, jmsTracing);
  }
}
