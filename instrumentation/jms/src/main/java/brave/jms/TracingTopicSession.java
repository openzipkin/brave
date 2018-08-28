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

  TracingTopicSession(TopicSession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
    TopicSubscriber ts = ((TopicSession) delegate).createSubscriber(topic);
    return (TopicSubscriber) TracingTopicSubscriber.create(ts, jmsTracing);
  }

  @Override
  public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal)
      throws JMSException {
    TopicSubscriber ts =
        ((TopicSession) delegate).createSubscriber(topic, messageSelector, noLocal);
    return (TopicSubscriber) TracingTopicSubscriber.create(ts, jmsTracing);
  }

  @Override public TopicPublisher createPublisher(Topic topic) throws JMSException {
    TopicPublisher tp = ((TopicSession) delegate).createPublisher(topic);
    return (TopicPublisher) TracingTopicPublisher.create(tp, jmsTracing);
  }
}
