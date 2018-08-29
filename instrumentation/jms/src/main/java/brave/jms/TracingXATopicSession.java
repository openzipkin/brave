package brave.jms;

import javax.jms.JMSException;
import javax.jms.TopicSession;
import javax.jms.XATopicSession;

final class TracingXATopicSession extends TracingXASession implements XATopicSession {
  TracingXATopicSession(XATopicSession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public TopicSession getTopicSession() throws JMSException {
    return TracingTopicSession.create(((XATopicSession) delegate).getTopicSession(), jmsTracing);
  }
}
