package brave.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.XAQueueSession;

class TracingQueueConnection extends TracingConnection implements QueueConnection {
  static QueueConnection create(QueueConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingQueueConnection) return delegate;
    if (delegate instanceof XAQueueSession) {
      return new TracingXAQueueConnection(delegate, jmsTracing);
    }
    return new TracingQueueConnection(delegate, jmsTracing);
  }

  TracingQueueConnection(QueueConnection delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public QueueSession createQueueSession(boolean transacted, int acknowledgeMode)
      throws JMSException {
    QueueSession ts = ((QueueConnection) delegate).createQueueSession(transacted, acknowledgeMode);
    return TracingQueueSession.create(ts, jmsTracing);
  }

  @Override public ConnectionConsumer createConnectionConsumer(Queue queue, String messageSelector,
      ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    ConnectionConsumer cc =
        delegate.createConnectionConsumer(queue, messageSelector, sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }
}
