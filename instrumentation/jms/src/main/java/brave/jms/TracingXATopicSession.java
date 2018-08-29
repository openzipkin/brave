package brave.jms;

import javax.jms.JMSException;
import javax.jms.TopicSession;
import javax.jms.XATopicSession;

final class TracingXATopicSession extends TracingXASession<XATopicSession>
    implements XATopicSession {
  TracingXATopicSession(XATopicSession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public TopicSession getTopicSession() throws JMSException {
    return TracingTopicSession.create(delegate.getTopicSession(), jmsTracing);
  }
}
