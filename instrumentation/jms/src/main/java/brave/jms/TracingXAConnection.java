package brave.jms;

import javax.jms.JMSException;
import javax.jms.XAConnection;
import javax.jms.XAQueueConnection;
import javax.jms.XASession;
import javax.jms.XATopicConnection;

final class TracingXAConnection extends TracingConnection<XAConnection> implements XAConnection {
  static XAConnection create(XAConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXAConnection) return delegate;
    if (delegate instanceof XATopicConnection) {
      return TracingXATopicConnection.create((XATopicConnection) delegate, jmsTracing);
    }
    if (delegate instanceof XAQueueConnection) {
      return TracingXAQueueConnection.create((XAQueueConnection) delegate, jmsTracing);
    }
    return new TracingXAConnection(delegate, jmsTracing);
  }

  TracingXAConnection(XAConnection delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public XASession createXASession() throws JMSException {
    return TracingXAQueueSession.create(delegate.createXASession(), jmsTracing);
  }
}

