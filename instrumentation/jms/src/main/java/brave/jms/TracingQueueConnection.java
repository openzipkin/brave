package brave.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueSession;

class TracingQueueConnection extends TracingConnection implements QueueConnection {
  static QueueConnection create(QueueConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingQueueConnection) return delegate;
    if (delegate instanceof XAQueueSession) {
      return new TracingXAQueueConnection((XAQueueConnection) delegate, jmsTracing);
    }
    return new TracingQueueConnection(delegate, jmsTracing);
  }

  final QueueConnection qc;

  TracingQueueConnection(QueueConnection delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    this.qc = delegate;
  }

  @Override public QueueSession createQueueSession(boolean transacted, int acknowledgeMode)
      throws JMSException {
    return TracingQueueSession.create(qc.createQueueSession(transacted, acknowledgeMode),
        jmsTracing);
  }

  @Override public ConnectionConsumer createConnectionConsumer(Queue queue, String messageSelector,
      ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    ConnectionConsumer cc =
        qc.createConnectionConsumer(queue, messageSelector, sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }
}
