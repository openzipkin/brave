package brave.jms;

import javax.jms.JMSException;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XATopicConnectionFactory;

class TracingTopicConnectionFactory extends TracingConnectionFactory
    implements TopicConnectionFactory {
  static TopicConnectionFactory create(TopicConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingTopicConnectionFactory) return delegate;
    if (delegate instanceof XATopicConnectionFactory) {
      return TracingXATopicConnectionFactory.create((XATopicConnectionFactory) delegate,
          jmsTracing);
    }
    return new TracingTopicConnectionFactory(delegate, jmsTracing);
  }

  final TopicConnectionFactory tcf;

  TracingTopicConnectionFactory(TopicConnectionFactory delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    this.tcf = delegate;
  }

  @Override public TopicConnection createTopicConnection() throws JMSException {
    return TracingTopicConnection.create(tcf.createTopicConnection(), jmsTracing);
  }

  @Override public TopicConnection createTopicConnection(String userName, String password)
      throws JMSException {
    return TracingTopicConnection.create(tcf.createTopicConnection(userName, password), jmsTracing);
  }
}
