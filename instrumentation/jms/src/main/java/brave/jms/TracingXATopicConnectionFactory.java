package brave.jms;

import javax.jms.JMSException;
import javax.jms.XAConnection;
import javax.jms.XAJMSContext;
import javax.jms.XATopicConnection;
import javax.jms.XATopicConnectionFactory;

final class TracingXATopicConnectionFactory extends TracingTopicConnectionFactory
    implements XATopicConnectionFactory {
  static XATopicConnectionFactory create(XATopicConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXATopicConnectionFactory) return delegate;
    return new TracingXATopicConnectionFactory(delegate, jmsTracing);
  }

  final XATopicConnectionFactory xatcf;

  TracingXATopicConnectionFactory(XATopicConnectionFactory delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    this.xatcf = delegate;
  }

  @Override public XATopicConnection createXATopicConnection() throws JMSException {
    return TracingXATopicConnection.create(xatcf.createXATopicConnection(), jmsTracing);
  }

  @Override public XATopicConnection createXATopicConnection(String userName, String password)
      throws JMSException {
    XATopicConnection xatc = xatcf.createXATopicConnection(userName, password);
    return TracingXATopicConnection.create(xatc, jmsTracing);
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
