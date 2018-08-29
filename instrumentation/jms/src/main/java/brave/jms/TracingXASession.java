package brave.jms;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.XAQueueSession;
import javax.jms.XASession;
import javax.jms.XATopicSession;
import javax.transaction.xa.XAResource;

class TracingXASession extends TracingSession implements XASession {
  static XASession create(XASession delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXASession) return delegate;
    if (delegate instanceof XAQueueSession) {
      return new TracingXAQueueSession((XAQueueSession) delegate, jmsTracing);
    }
    if (delegate instanceof XATopicSession) {
      return new TracingXATopicSession((XATopicSession) delegate, jmsTracing);
    }
    return new TracingXASession(delegate, jmsTracing);
  }

  final XASession xas;

  TracingXASession(XASession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    xas = delegate;
  }

  @Override public Session getSession() throws JMSException {
    return TracingSession.create(xas.getSession(), jmsTracing);
  }

  @Override public XAResource getXAResource() {
    return xas.getXAResource();
  }
}
