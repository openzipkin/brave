package brave.jms;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueSession;
import javax.jms.XASession;

final class TracingXAQueueConnection extends TracingQueueConnection implements XAQueueConnection {
  static XAQueueConnection create(XAQueueConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXAQueueConnection) return delegate;
    return new TracingXAQueueConnection(delegate, jmsTracing);
  }

  TracingXAQueueConnection(QueueConnection delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public XAQueueSession createXAQueueSession() throws JMSException {
    XAQueueSession xts = ((XAQueueConnection) delegate).createXAQueueSession();
    return new TracingXAQueueSession(xts, jmsTracing);
  }

  @Override public XASession createXASession() throws JMSException {
    XASession xs = ((XAQueueConnection) delegate).createXASession();
    return TracingXAQueueSession.create(xs, jmsTracing);
  }
}

