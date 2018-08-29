package brave.jms;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.XAQueueConnectionFactory;

class TracingQueueConnectionFactory<C extends QueueConnectionFactory>
    extends TracingConnectionFactory<C> implements QueueConnectionFactory {
  static QueueConnectionFactory create(QueueConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingQueueConnectionFactory) return delegate;
    if (delegate instanceof XAQueueConnectionFactory) {
      return TracingXAQueueConnectionFactory.create((XAQueueConnectionFactory) delegate,
          jmsTracing);
    }
    return new TracingQueueConnectionFactory<>(delegate, jmsTracing);
  }

  TracingQueueConnectionFactory(C delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public QueueConnection createQueueConnection() throws JMSException {
    return TracingQueueConnection.create(delegate.createQueueConnection(), jmsTracing);
  }

  @Override public QueueConnection createQueueConnection(String userName, String password)
      throws JMSException {
    return TracingQueueConnection.create(delegate.createQueueConnection(userName, password),
        jmsTracing);
  }
}
