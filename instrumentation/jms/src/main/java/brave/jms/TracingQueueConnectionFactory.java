package brave.jms;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.XAQueueConnectionFactory;

class TracingQueueConnectionFactory extends TracingConnectionFactory
    implements QueueConnectionFactory {
  static QueueConnectionFactory create(QueueConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingQueueConnectionFactory) return delegate;
    if (delegate instanceof XAQueueConnectionFactory) {
      return TracingXAQueueConnectionFactory.create((XAQueueConnectionFactory) delegate,
          jmsTracing);
    }
    return new TracingQueueConnectionFactory(delegate, jmsTracing);
  }

  final QueueConnectionFactory tcf;

  TracingQueueConnectionFactory(QueueConnectionFactory delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    this.tcf = delegate;
  }

  @Override public QueueConnection createQueueConnection() throws JMSException {
    return TracingQueueConnection.create(tcf.createQueueConnection(), jmsTracing);
  }

  @Override public QueueConnection createQueueConnection(String userName, String password)
      throws JMSException {
    return TracingQueueConnection.create(tcf.createQueueConnection(userName, password), jmsTracing);
  }
}
