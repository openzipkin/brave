package brave.jms;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.XAQueueSession;
import javax.jms.XASession;
import javax.jms.XATopicSession;
import javax.transaction.xa.XAResource;

class TracingXASession<S extends XASession> extends TracingSession<S> implements XASession {
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

  TracingXASession(S delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public Session getSession() throws JMSException {
    return TracingSession.create(delegate.getSession(), jmsTracing);
  }

  @Override public XAResource getXAResource() {
    return delegate.getXAResource();
  }
}
