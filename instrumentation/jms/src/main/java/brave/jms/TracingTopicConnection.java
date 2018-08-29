package brave.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.jms.XATopicSession;

class TracingTopicConnection<C extends TopicConnection> extends TracingConnection<C>
    implements TopicConnection {
  static TopicConnection create(TopicConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingTopicConnection) return delegate;
    if (delegate instanceof XATopicSession) {
      return TracingXATopicConnection.create(delegate, jmsTracing);
    }
    return new TracingTopicConnection<>(delegate, jmsTracing);
  }

  TracingTopicConnection(C delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public TopicSession createTopicSession(boolean transacted, int acknowledgeMode)
      throws JMSException {
    TopicSession ts = delegate.createTopicSession(transacted, acknowledgeMode);
    return TracingTopicSession.create(ts, jmsTracing);
  }

  @Override public ConnectionConsumer createConnectionConsumer(Topic topic, String messageSelector,
      ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    ConnectionConsumer cc =
        delegate.createConnectionConsumer(topic, messageSelector, sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }
}
