package brave.jms;

import javax.jms.JMSException;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XAJMSContext;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnectionFactory;

class TracingXAConnectionFactory implements XAConnectionFactory {
  static XAConnectionFactory create(XAConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("xaConnectionFactory == null");
    if (delegate instanceof TracingXAConnectionFactory) return delegate;
    if (delegate instanceof XAQueueConnectionFactory) {
      return TracingXAQueueConnectionFactory.create((XAQueueConnectionFactory) delegate,
          jmsTracing);
    }
    if (delegate instanceof XATopicConnectionFactory) {
      return TracingXATopicConnectionFactory.create((XATopicConnectionFactory) delegate,
          jmsTracing);
    }
    return new TracingXAConnectionFactory(delegate, jmsTracing);
  }

  final XAConnectionFactory delegate;
  final JmsTracing jmsTracing;

  TracingXAConnectionFactory(XAConnectionFactory delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
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
