package brave.jms;

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

final class TracingTopicSession extends TracingSession implements TopicSession {
  static TopicSession create(TopicSession delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("topicSession == null");
    if (delegate instanceof TracingTopicSession) return delegate;
    return new TracingTopicSession(delegate, jmsTracing);
  }

  final TopicSession ts;

  TracingTopicSession(TopicSession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    ts = delegate;
  }

  @Override public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
    return TracingTopicSubscriber.create(ts.createSubscriber(topic), jmsTracing);
  }

  @Override
  public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal)
      throws JMSException {
    return TracingTopicSubscriber.create(ts.createSubscriber(topic, messageSelector, noLocal),
        jmsTracing);
  }

  @Override public TopicPublisher createPublisher(Topic topic) throws JMSException {
    return TracingTopicPublisher.create(ts.createPublisher(topic), jmsTracing);
  }
}
