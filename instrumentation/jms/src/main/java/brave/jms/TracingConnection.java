/*
 * Copyright 2013-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.jms;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;
import javax.jms.XAConnection;
import javax.jms.XAQueueConnection;
import javax.jms.XATopicConnection;

/** Implements all interfaces as according to ActiveMQ, this is typical of JMS 1.1. */
class TracingConnection implements QueueConnection, TopicConnection {
  static final int
    TYPE_QUEUE = 1 << 1,
    TYPE_TOPIC = 1 << 2,
    TYPE_XA = 1 << 3,
    TYPE_XA_QUEUE = 1 << 4,
    TYPE_XA_TOPIC = 1 << 5;

  static TracingConnection create(Connection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingConnection) return (TracingConnection) delegate;
    return new TracingConnection(delegate, jmsTracing);
  }

  final Connection delegate;
  final JmsTracing jmsTracing;
  final int types;

  TracingConnection(Connection delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    int types = 0;
    if (delegate instanceof QueueConnection) types |= TYPE_QUEUE;
    if (delegate instanceof TopicConnection) types |= TYPE_TOPIC;
    if (delegate instanceof XAConnection) types |= TYPE_XA;
    if (delegate instanceof XAQueueConnection) types |= TYPE_XA_QUEUE;
    if (delegate instanceof XATopicConnection) types |= TYPE_XA_TOPIC;
    this.types = types;
  }

  @Override public Session createSession(boolean transacted, int acknowledgeMode)
    throws JMSException {
    return TracingSession.create(delegate.createSession(transacted, acknowledgeMode), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0
  public Session createSession(int sessionMode) throws JMSException {
    return TracingSession.create(delegate.createSession(sessionMode), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0
  public Session createSession() throws JMSException {
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

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0
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

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0
  public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic,
    String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
    int maxMessages) throws JMSException {
    ConnectionConsumer cc =
      delegate.createSharedDurableConnectionConsumer(topic, subscriptionName, messageSelector,
        sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }

  // QueueConnection
  @Override public QueueSession createQueueSession(boolean transacted, int acknowledgeMode)
    throws JMSException {
    checkQueueConnection();
    QueueSession qs = ((QueueConnection) delegate).createQueueSession(transacted, acknowledgeMode);
    return TracingSession.create(qs, jmsTracing);
  }

  @Override public ConnectionConsumer createConnectionConsumer(Queue queue, String messageSelector,
    ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    checkQueueConnection();
    ConnectionConsumer cc = ((QueueConnection) delegate)
      .createConnectionConsumer(queue, messageSelector, sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }

  void checkQueueConnection() {
    if ((types & TYPE_QUEUE) != TYPE_QUEUE) {
      throw new IllegalStateException(delegate + " is not a QueueConnection");
    }
  }

  // TopicConnection
  @Override public TopicSession createTopicSession(boolean transacted, int acknowledgeMode)
    throws JMSException {
    checkTopicConnection();
    TopicSession ts = ((TopicConnection) delegate).createTopicSession(transacted, acknowledgeMode);
    return TracingSession.create(ts, jmsTracing);
  }

  @Override public ConnectionConsumer createConnectionConsumer(Topic topic, String messageSelector,
    ServerSessionPool sessionPool, int maxMessages) throws JMSException {
    checkTopicConnection();
    ConnectionConsumer cc = ((TopicConnection) delegate)
      .createConnectionConsumer(topic, messageSelector, sessionPool, maxMessages);
    return TracingConnectionConsumer.create(cc, jmsTracing);
  }

  void checkTopicConnection() {
    if ((types & TYPE_TOPIC) != TYPE_TOPIC) {
      throw new IllegalStateException(delegate + " is not a TopicConnection");
    }
  }
}
