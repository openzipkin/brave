package brave.jms;

import javax.jms.JMSException;
import javax.jms.XAConnection;
import javax.jms.XAJMSContext;
import javax.jms.XATopicConnection;
import javax.jms.XATopicConnectionFactory;

final class TracingXATopicConnectionFactory
    extends TracingTopicConnectionFactory<XATopicConnectionFactory>
    implements XATopicConnectionFactory {

  TracingXATopicConnectionFactory(XATopicConnectionFactory delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public XATopicConnection createXATopicConnection() throws JMSException {
    return TracingXATopicConnection.create(delegate.createXATopicConnection(), jmsTracing);
  }

  @Override public XATopicConnection createXATopicConnection(String userName, String password)
      throws JMSException {
    XATopicConnection xatc = delegate.createXATopicConnection(userName, password);
    return TracingXATopicConnection.create(xatc, jmsTracing);
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
