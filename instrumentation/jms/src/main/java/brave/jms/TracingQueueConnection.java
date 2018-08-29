package brave.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueSession;

class TracingQueueConnection<C extends QueueConnection> extends TracingConnection<C>
    implements QueueConnection {
  static QueueConnection create(QueueConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingQueueConnection) return delegate;
    if (delegate instanceof XAQueueSession) {
      return new TracingXAQueueConnection((XAQueueConnection) delegate, jmsTracing);
    }
    return new TracingQueueConnection<>(delegate, jmsTracing);
  }

  TracingQueueConnection(C delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public QueueSession createQueueSession(boolean transacted, int acknowledgeMode)
      throws JMSException {
    QueueSession qs = delegate.createQueueSession(transacted, acknowledgeMode);
    return TracingQueueSession.create(qs, jmsTracing);
  }

  @Override public ConnectionConsumer createConnectionConsumer(Queue queue, String messageSelector,
      ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    ConnectionConsumer cc =
        delegate.createConnectionConsumer(queue, messageSelector, sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }
}
