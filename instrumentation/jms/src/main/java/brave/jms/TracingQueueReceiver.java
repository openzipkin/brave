package brave.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueReceiver;

final class TracingQueueReceiver extends TracingMessageConsumer implements QueueReceiver {
  static QueueReceiver create(QueueReceiver delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("queueReceiver == null");
    if (delegate instanceof TracingQueueReceiver) return delegate;
    return new TracingQueueReceiver(delegate, jmsTracing);
  }

  TracingQueueReceiver(QueueReceiver delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public Queue getQueue() throws JMSException {
    return ((QueueReceiver) delegate).getQueue();
  }
}
