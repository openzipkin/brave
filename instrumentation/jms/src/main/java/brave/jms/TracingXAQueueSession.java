package brave.jms;

import javax.jms.JMSException;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.XAQueueSession;

final class TracingXAQueueSession extends TracingXASession implements XAQueueSession {
  TracingXAQueueSession(Session delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public QueueSession getQueueSession() throws JMSException {
    return TracingQueueSession.create(((XAQueueSession) delegate).getQueueSession(), jmsTracing);
  }
}
