package brave.jms;

import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XATopicConnectionFactory;

class TracingTopicConnectionFactory<C extends TopicConnectionFactory>
    extends TracingConnectionFactory<C>
    implements TopicConnectionFactory {
  static TopicConnectionFactory create(TopicConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingTopicConnectionFactory) return delegate;
    if (delegate instanceof XATopicConnectionFactory) {
      return new TracingXATopicConnectionFactory((XATopicConnectionFactory) delegate, jmsTracing);
    }
    return new TracingTopicConnectionFactory<>(delegate, jmsTracing);
  }

  TracingTopicConnectionFactory(C delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public TopicConnection createTopicConnection() throws JMSException {
    return TracingTopicConnection.create(delegate.createTopicConnection(), jmsTracing);
  }

  @Override public TopicConnection createTopicConnection(String userName, String password)
      throws JMSException {
    return TracingTopicConnection.create(delegate.createTopicConnection(userName, password),
        jmsTracing);
  }
}
