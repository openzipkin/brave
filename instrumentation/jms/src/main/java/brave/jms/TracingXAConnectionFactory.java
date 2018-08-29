package brave.jms;

import javax.jms.JMSException;
import javax.jms.XAConnection;
import javax.jms.XAConnectionFactory;
import javax.jms.XAJMSContext;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnectionFactory;

final class TracingXAConnectionFactory implements XAConnectionFactory {
  static XAConnectionFactory create(XAConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("xaConnectionFactory == null");
    if (delegate instanceof TracingXAConnectionFactory) return delegate;
    if (delegate instanceof XAQueueConnectionFactory) {
      return new TracingXAQueueConnectionFactory((XAQueueConnectionFactory) delegate, jmsTracing);
    }
    if (delegate instanceof XATopicConnectionFactory) {
      return new TracingXATopicConnectionFactory((XATopicConnectionFactory) delegate, jmsTracing);
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

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public XAJMSContext createXAContext() {
    return TracingXAJMSContext.create(delegate.createXAContext(), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public XAJMSContext createXAContext(String userName, String password) {
    return TracingXAJMSContext.create(delegate.createXAContext(userName, password), jmsTracing);
  }
}
