package brave.jms;

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

final class TracingTopicSubscriber extends TracingMessageConsumer implements TopicSubscriber {

  TracingTopicSubscriber(TopicSubscriber delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public Topic getTopic() throws JMSException {
    return ((TopicSubscriber) delegate).getTopic();
  }

  @Override public boolean getNoLocal() throws JMSException {
    return ((TopicSubscriber) delegate).getNoLocal();
  }
}
