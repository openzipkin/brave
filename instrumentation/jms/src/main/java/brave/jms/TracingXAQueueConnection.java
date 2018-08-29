package brave.jms;

import javax.jms.JMSException;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueSession;
import javax.jms.XASession;

final class TracingXAQueueConnection extends TracingQueueConnection implements XAQueueConnection {
  static XAQueueConnection create(XAQueueConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXAQueueConnection) return delegate;
    return new TracingXAQueueConnection(delegate, jmsTracing);
  }

  final XAQueueConnection xaqc;

  TracingXAQueueConnection(XAQueueConnection delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    xaqc = delegate;
  }

  @Override public XAQueueSession createXAQueueSession() throws JMSException {
    return new TracingXAQueueSession(xaqc.createXAQueueSession(), jmsTracing);
  }

  @Override public XASession createXASession() throws JMSException {
    return TracingXAQueueSession.create(xaqc.createXASession(), jmsTracing);
  }
}

