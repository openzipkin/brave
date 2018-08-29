package brave.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnectionFactory;

class TracingConnectionFactory implements ConnectionFactory {
  static ConnectionFactory create(ConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("connectionFactory == null");
    if (delegate instanceof TracingConnectionFactory) return delegate;
    if (delegate instanceof QueueConnectionFactory) {
      return TracingQueueConnectionFactory.create((QueueConnectionFactory) delegate, jmsTracing);
    }
    if (delegate instanceof TopicConnectionFactory) {
      return TracingTopicConnectionFactory.create((TopicConnectionFactory) delegate, jmsTracing);
    }
    return new TracingConnectionFactory(delegate, jmsTracing);
  }

  final ConnectionFactory delegate;
  final JmsTracing jmsTracing;

  TracingConnectionFactory(ConnectionFactory delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
  }

  @Override public Connection createConnection() throws JMSException {
    return TracingConnection.create(delegate.createConnection(), jmsTracing);
  }

  @Override public Connection createConnection(String userName, String password)
      throws JMSException {
    return TracingConnection.create(delegate.createConnection(userName, password), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public JMSContext createContext() {
    return TracingJMSContext.create(delegate.createContext(), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public JMSContext createContext(String userName, String password) {
    return TracingJMSContext.create(delegate.createContext(userName, password), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public JMSContext createContext(String userName, String password, int sessionMode) {
    JMSContext cDelegate = delegate.createContext(userName, password, sessionMode);
    return TracingJMSContext.create(cDelegate, jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public JMSContext createContext(int sessionMode) {
    return TracingJMSContext.create(delegate.createContext(sessionMode), jmsTracing);
  }
}
