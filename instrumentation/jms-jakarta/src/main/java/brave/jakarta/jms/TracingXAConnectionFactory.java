/*
 * Copyright 2013-2023 The OpenZipkin Authors
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
package brave.jakarta.jms;

import jakarta.jms.JMSException;
import jakarta.jms.XAConnection;
import jakarta.jms.XAConnectionFactory;
import jakarta.jms.XAJMSContext;
import jakarta.jms.XAQueueConnection;
import jakarta.jms.XAQueueConnectionFactory;
import jakarta.jms.XATopicConnection;
import jakarta.jms.XATopicConnectionFactory;

import static brave.jakarta.jms.TracingConnection.TYPE_XA_QUEUE;
import static brave.jakarta.jms.TracingConnection.TYPE_XA_TOPIC;

/** Implements all interfaces as according to ActiveMQ, this is typical of JMS 1.1. */
final class TracingXAConnectionFactory extends TracingConnectionFactory
  implements XAQueueConnectionFactory, XATopicConnectionFactory {

  static XAConnectionFactory create(XAConnectionFactory delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("xaConnectionFactory == null");
    if (delegate instanceof TracingXAConnectionFactory) return delegate;
    return new TracingXAConnectionFactory(delegate, jmsTracing);
  }

  TracingXAConnectionFactory(XAConnectionFactory delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public XAConnection createXAConnection() throws JMSException {
    XAConnectionFactory xacf = (XAConnectionFactory) delegate;
    return TracingXAConnection.create(xacf.createXAConnection(), jmsTracing);
  }

  @Override public XAConnection createXAConnection(String userName, String password)
    throws JMSException {
    XAConnectionFactory xacf = (XAConnectionFactory) delegate;
    return TracingXAConnection.create(xacf.createXAConnection(userName, password), jmsTracing);
  }

  public XAJMSContext createXAContext() {
    XAConnectionFactory xacf = (XAConnectionFactory) delegate;
    return TracingXAJMSContext.create(xacf.createXAContext(), jmsTracing);
  }

  public XAJMSContext createXAContext(String userName, String password) {
    XAConnectionFactory xacf = (XAConnectionFactory) delegate;
    return TracingXAJMSContext.create(xacf.createXAContext(userName, password), jmsTracing);
  }

  // XAQueueConnectionFactory

  @Override public XAQueueConnection createXAQueueConnection() throws JMSException {
    checkQueueConnectionFactory();
    XAQueueConnectionFactory xaqcf = (XAQueueConnectionFactory) delegate;
    return TracingXAConnection.create(xaqcf.createXAQueueConnection(), jmsTracing);
  }

  @Override public XAQueueConnection createXAQueueConnection(String userName, String password)
    throws JMSException {
    checkQueueConnectionFactory();
    XAQueueConnectionFactory xaqcf = (XAQueueConnectionFactory) delegate;
    return TracingXAConnection.create(xaqcf.createXAQueueConnection(userName, password),
      jmsTracing);
  }

  void checkQueueConnectionFactory() {
    if ((types & TYPE_XA_QUEUE) != TYPE_XA_QUEUE) {
      throw new IllegalStateException(delegate + " is not an XAQueueConnectionFactory");
    }
  }

  // XATopicConnectionFactory

  @Override public XATopicConnection createXATopicConnection() throws JMSException {
    checkTopicConnectionFactory();
    XATopicConnectionFactory xaqcf = (XATopicConnectionFactory) delegate;
    return TracingXAConnection.create(xaqcf.createXATopicConnection(), jmsTracing);
  }

  @Override public XATopicConnection createXATopicConnection(String userName, String password)
    throws JMSException {
    checkTopicConnectionFactory();
    XATopicConnectionFactory xaqcf = (XATopicConnectionFactory) delegate;
    return TracingXAConnection.create(xaqcf.createXATopicConnection(userName, password),
      jmsTracing);
  }

  void checkTopicConnectionFactory() {
    if ((types & TYPE_XA_TOPIC) != TYPE_XA_TOPIC) {
      throw new IllegalStateException(delegate + " is not an XATopicConnectionFactory");
    }
  }
}
