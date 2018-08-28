package brave.jms;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TopicSession;
import javax.jms.XATopicSession;

final class TracingXATopicSession extends TracingXASession implements XATopicSession {
  TracingXATopicSession(Session delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public TopicSession getTopicSession() throws JMSException {
    return TracingTopicSession.create(((XATopicSession) delegate).getTopicSession(), jmsTracing);
  }
}
