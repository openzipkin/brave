package brave.jms;

import javax.jms.JMSException;
import javax.jms.XAConnection;
import javax.jms.XAJMSContext;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueConnectionFactory;

final class TracingXAQueueConnectionFactory extends TracingQueueConnectionFactory
    implements XAQueueConnectionFactory {
  static XAQueueConnectionFactory create(XAQueueConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXAQueueConnectionFactory) return delegate;
    return new TracingXAQueueConnectionFactory(delegate, jmsTracing);
  }

  final XAQueueConnectionFactory xatcf;

  TracingXAQueueConnectionFactory(XAQueueConnectionFactory delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    this.xatcf = delegate;
  }

  @Override public XAQueueConnection createXAQueueConnection() throws JMSException {
    return TracingXAQueueConnection.create(xatcf.createXAQueueConnection(), jmsTracing);
  }

  @Override public XAQueueConnection createXAQueueConnection(String userName, String password)
      throws JMSException {
    XAQueueConnection xatc = xatcf.createXAQueueConnection(userName, password);
    return TracingXAQueueConnection.create(xatc, jmsTracing);
  }

  @Override public XAConnection createXAConnection() throws JMSException {
    return TracingXAConnection.create(xatcf.createXAConnection(), jmsTracing);
  }

  @Override public XAConnection createXAConnection(String userName, String password)
      throws JMSException {
    return TracingXAConnection.create(xatcf.createXAConnection(userName, password), jmsTracing);
  }

  @Override public XAJMSContext createXAContext() {
    return TracingXAJMSContext.create(xatcf.createXAContext(), jmsTracing);
  }

  @Override public XAJMSContext createXAContext(String userName, String password) {
    return TracingXAJMSContext.create(xatcf.createXAContext(userName, password), jmsTracing);
  }
}
