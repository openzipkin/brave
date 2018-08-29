package brave.jms;

import javax.jms.JMSException;
import javax.jms.XASession;
import javax.jms.XATopicConnection;
import javax.jms.XATopicSession;

final class TracingXATopicConnection extends TracingTopicConnection<XATopicConnection>
    implements XATopicConnection {
  static XATopicConnection create(XATopicConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXATopicConnection) return delegate;
    return new TracingXATopicConnection(delegate, jmsTracing);
  }

  TracingXATopicConnection(XATopicConnection delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public XATopicSession createXATopicSession() throws JMSException {
    return new TracingXATopicSession(delegate.createXATopicSession(), jmsTracing);
  }

  @Override public XASession createXASession() throws JMSException {
    return TracingXATopicSession.create(delegate.createXASession(), jmsTracing);
  }
}
