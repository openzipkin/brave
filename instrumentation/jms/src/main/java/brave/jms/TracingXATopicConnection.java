package brave.jms;

import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.XASession;
import javax.jms.XATopicConnection;
import javax.jms.XATopicSession;

final class TracingXATopicConnection extends TracingTopicConnection implements XATopicConnection {
  static XATopicConnection create(XATopicConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXATopicConnection) return delegate;
    return new TracingXATopicConnection(delegate, jmsTracing);
  }

  TracingXATopicConnection(TopicConnection delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public XATopicSession createXATopicSession() throws JMSException {
    XATopicSession xts = ((XATopicConnection) delegate).createXATopicSession();
    return new TracingXATopicSession(xts, jmsTracing);
  }

  @Override public XASession createXASession() throws JMSException {
    XASession xs = ((XATopicConnection) delegate).createXASession();
    return TracingXATopicSession.create(xs, jmsTracing);
  }
}
