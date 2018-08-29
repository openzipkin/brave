package brave.jms;

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

final class TracingTopicSubscriber extends TracingMessageConsumer<TopicSubscriber>
    implements TopicSubscriber {
  static TopicSubscriber create(TopicSubscriber delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("topicSubscriber == null");
    if (delegate instanceof TracingTopicSubscriber) return delegate;
    return new TracingTopicSubscriber(delegate, jmsTracing);
  }

  TracingTopicSubscriber(TopicSubscriber delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public Topic getTopic() throws JMSException {
    return delegate.getTopic();
  }

  @Override public boolean getNoLocal() throws JMSException {
    return delegate.getNoLocal();
  }
}
