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
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.XAConnectionFactory;
import javax.jms.XAQueueConnectionFactory;
import javax.jms.XATopicConnectionFactory;

/** Implements all interfaces as according to ActiveMQ, this is typical of JMS 1.1. */
class TracingConnectionFactory implements QueueConnectionFactory, TopicConnectionFactory {
  static final int
    TYPE_CF = 1 << 1,
    TYPE_QUEUE_CF = 1 << 2,
    TYPE_TOPIC_CF = 1 << 3,
    TYPE_XA_CF = 1 << 4,
    TYPE_XA_QUEUE_CF = 1 << 5,
    TYPE_XA_TOPIC_CF = 1 << 6;

  static ConnectionFactory create(ConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("connectionFactory == null");
    if (delegate instanceof TracingConnectionFactory) return delegate;
    return new TracingConnectionFactory(delegate, jmsTracing);
  }

  // Object because ConnectionFactory and XAConnectionFactory share no common root
  final Object delegate;
  final JmsTracing jmsTracing;
  final int types;

  TracingConnectionFactory(Object delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    int types = 0;
    if (delegate instanceof ConnectionFactory) types |= TYPE_CF;
    if (delegate instanceof QueueConnectionFactory) types |= TYPE_QUEUE_CF;
    if (delegate instanceof TopicConnectionFactory) types |= TYPE_TOPIC_CF;
    if (delegate instanceof XAConnectionFactory) types |= TYPE_XA_CF;
    if (delegate instanceof XAQueueConnectionFactory) types |= TYPE_XA_QUEUE_CF;
    if (delegate instanceof XATopicConnectionFactory) types |= TYPE_XA_TOPIC_CF;
    this.types = types;
  }

  @Override public Connection createConnection() throws JMSException {
    checkConnectionFactory();
    return TracingConnection.create(((ConnectionFactory) delegate).createConnection(), jmsTracing);
  }

  @Override public Connection createConnection(String userName, String password)
    throws JMSException {
    checkConnectionFactory();
    ConnectionFactory cf = (ConnectionFactory) delegate;
    return TracingConnection.create(cf.createConnection(userName, password), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public JMSContext createContext() {
    checkConnectionFactory();
    return TracingJMSContext.create(((ConnectionFactory) delegate).createContext(), jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public JMSContext createContext(String userName, String password) {
    checkConnectionFactory();
    JMSContext ctx = ((ConnectionFactory) delegate).createContext(userName, password);
    return TracingJMSContext.create(ctx, jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public JMSContext createContext(String userName, String password, int sessionMode) {
    checkConnectionFactory();
    JMSContext ctx = ((ConnectionFactory) delegate).createContext(userName, password, sessionMode);
    return TracingJMSContext.create(ctx, jmsTracing);
  }

  /* @Override JMS 2.0 method: Intentionally no override to ensure JMS 1.1 works! */
  @JMS2_0 public JMSContext createContext(int sessionMode) {
    checkConnectionFactory();
    JMSContext ctx = ((ConnectionFactory) delegate).createContext(sessionMode);
    return TracingJMSContext.create(ctx, jmsTracing);
  }

  /**
   * We have to check for what seems base case as the constructor is shared by {@link
   * TracingXAConnection}, which is might not be a {@link ConnectionFactory}!
   */
  void checkConnectionFactory() {
    if ((types & TYPE_CF) != TYPE_CF) {
      throw new IllegalStateException(delegate + " is not a ConnectionFactory");
    }
  }

  // QueueConnectionFactory

  @Override public QueueConnection createQueueConnection() throws JMSException {
    checkQueueConnectionFactory();
    QueueConnectionFactory qcf = (QueueConnectionFactory) delegate;
    return TracingConnection.create(qcf.createQueueConnection(), jmsTracing);
  }

  @Override public QueueConnection createQueueConnection(String userName, String password)
    throws JMSException {
    checkQueueConnectionFactory();
    QueueConnectionFactory qcf = (QueueConnectionFactory) delegate;
    return TracingConnection.create(qcf.createQueueConnection(userName, password), jmsTracing);
  }

  void checkQueueConnectionFactory() {
    if ((types & TYPE_QUEUE_CF) != TYPE_QUEUE_CF) {
      throw new IllegalStateException(delegate + " is not a QueueConnectionFactory");
    }
  }

  // TopicConnectionFactory

  @Override public TopicConnection createTopicConnection() throws JMSException {
    checkTopicConnectionFactory();
    TopicConnectionFactory qcf = (TopicConnectionFactory) delegate;
    return TracingConnection.create(qcf.createTopicConnection(), jmsTracing);
  }

  @Override public TopicConnection createTopicConnection(String userName, String password)
    throws JMSException {
    checkTopicConnectionFactory();
    TopicConnectionFactory qcf = (TopicConnectionFactory) delegate;
    return TracingConnection.create(qcf.createTopicConnection(userName, password), jmsTracing);
  }

  void checkTopicConnectionFactory() {
    if ((types & TYPE_TOPIC_CF) != TYPE_TOPIC_CF) {
      throw new IllegalStateException(delegate + " is not a TopicConnectionFactory");
    }
  }
}
