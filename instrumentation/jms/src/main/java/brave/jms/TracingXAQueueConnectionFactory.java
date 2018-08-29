package brave.jms;

import javax.jms.JMSException;
import javax.jms.XAConnection;
import javax.jms.XAJMSContext;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueConnectionFactory;

final class TracingXAQueueConnectionFactory
    extends TracingQueueConnectionFactory<XAQueueConnectionFactory>
    implements XAQueueConnectionFactory {

  TracingXAQueueConnectionFactory(XAQueueConnectionFactory delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public XAQueueConnection createXAQueueConnection() throws JMSException {
    return TracingXAQueueConnection.create(delegate.createXAQueueConnection(), jmsTracing);
  }

  @Override public XAQueueConnection createXAQueueConnection(String userName, String password)
      throws JMSException {
    XAQueueConnection xatc = delegate.createXAQueueConnection(userName, password);
    return TracingXAQueueConnection.create(xatc, jmsTracing);
  }

  @Override public XAConnection createXAConnection() throws JMSException {
    return TracingXAConnection.create(delegate.createXAConnection(), jmsTracing);
  }

  @Override public XAConnection createXAConnection(String userName, String password)
      throws JMSException {
    return TracingXAConnection.create(delegate.createXAConnection(userName, password), jmsTracing);
  }

  @Override public XAJMSContext createXAContext() {
    return TracingXAJMSContext.create(delegate.createXAContext(), jmsTracing);
  }

  @Override public XAJMSContext createXAContext(String userName, String password) {
    return TracingXAJMSContext.create(delegate.createXAContext(userName, password), jmsTracing);
  }
}
