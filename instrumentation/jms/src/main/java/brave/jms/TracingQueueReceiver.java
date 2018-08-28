package brave.jms;

import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueReceiver;

final class TracingQueueReceiver extends TracingMessageConsumer implements QueueReceiver {

  TracingQueueReceiver(QueueReceiver delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public Queue getQueue() throws JMSException {
    return ((QueueReceiver) delegate).getQueue();
  }
}
