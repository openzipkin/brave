package brave.jms;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;

class TracingConnection implements Connection {
  static Connection create(Connection delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("connection == null");
    if (delegate instanceof TracingConnection) return delegate;
    if (delegate instanceof QueueConnection) {
      return TracingQueueConnection.create((QueueConnection) delegate, jmsTracing);
    }
    if (delegate instanceof TopicConnection) {
      return TracingTopicConnection.create((TopicConnection) delegate, jmsTracing);
    }
    if (delegate instanceof XAConnection) {
      return new TracingXAConnection((XAConnection) delegate, jmsTracing);
    }
    return new TracingConnection(delegate, jmsTracing);
  }

  final Connection delegate;
  final JmsTracing jmsTracing;

  TracingConnection(Connection delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
  }

  @Override public Session createSession(boolean transacted, int acknowledgeMode)
      throws JMSException {
    return TracingSession.create(delegate.createSession(transacted, acknowledgeMode), jmsTracing);
  }

  @Override public Session createSession(int sessionMode) throws JMSException {
    return TracingSession.create(delegate.createSession(sessionMode), jmsTracing);
  }

  @Override public Session createSession() throws JMSException {
    return TracingSession.create(delegate.createSession(), jmsTracing);
  }

  @Override public String getClientID() throws JMSException {
    return delegate.getClientID();
  }

  @Override public void setClientID(String clientID) throws JMSException {
    delegate.setClientID(clientID);
  }

  @Override public ConnectionMetaData getMetaData() throws JMSException {
    return delegate.getMetaData();
  }

  @Override public ExceptionListener getExceptionListener() throws JMSException {
    return delegate.getExceptionListener();
  }

  @Override public void setExceptionListener(ExceptionListener listener) throws JMSException {
    delegate.setExceptionListener(TracingExceptionListener.create(listener, jmsTracing));
  }

  @Override public void start() throws JMSException {
    delegate.start();
  }

  @Override public void stop() throws JMSException {
    delegate.stop();
  }

  @Override public void close() throws JMSException {
    delegate.close();
  }

  @Override public ConnectionConsumer createConnectionConsumer(Destination destination,
      String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    ConnectionConsumer cc =
        delegate.createConnectionConsumer(destination, messageSelector, sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }

  @Override
  public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName,
      String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    ConnectionConsumer cc =
        delegate.createSharedConnectionConsumer(topic, subscriptionName, messageSelector,
            sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }

  @Override
  public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName,
      String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    ConnectionConsumer cc =
        delegate.createDurableConnectionConsumer(topic, subscriptionName, messageSelector,
            sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }

  @Override public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic,
      String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
      int maxMessages) throws JMSException {
    ConnectionConsumer cc =
        delegate.createSharedDurableConnectionConsumer(topic, subscriptionName, messageSelector,
            sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }
}
