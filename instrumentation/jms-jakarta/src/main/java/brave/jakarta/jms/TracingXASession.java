/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import jakarta.jms.JMSException;
import jakarta.jms.QueueSession;
import jakarta.jms.Session;
import jakarta.jms.TopicSession;
import jakarta.jms.XAQueueSession;
import jakarta.jms.XASession;
import jakarta.jms.XATopicSession;
import javax.transaction.xa.XAResource;

import static brave.jakarta.jms.TracingConnection.TYPE_XA_QUEUE;
import static brave.jakarta.jms.TracingConnection.TYPE_XA_TOPIC;

/** Implements all interfaces as according to ActiveMQ, this is typical of JMS 1.1. */
final class TracingXASession extends TracingSession implements XATopicSession, XAQueueSession {

  static TracingXASession create(XASession delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXASession) return (TracingXASession) delegate;
    return new TracingXASession(delegate, jmsTracing);
  }

  TracingXASession(XASession delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public Session getSession() throws JMSException {
    return TracingSession.create(((XASession) delegate).getSession(), jmsTracing);
  }

  @Override public XAResource getXAResource() {
    return ((XASession) delegate).getXAResource();
  }

  @Override public QueueSession getQueueSession() throws JMSException {
    if ((types & TYPE_XA_QUEUE) != TYPE_XA_QUEUE) {
      throw new IllegalStateException(delegate + " is not an XAQueueSession");
    }
    QueueSession xats = ((XAQueueSession) delegate).getQueueSession();
    return TracingSession.create(xats, jmsTracing);
  }

  @Override public TopicSession getTopicSession() throws JMSException {
    if ((types & TYPE_XA_TOPIC) != TYPE_XA_TOPIC) {
      throw new IllegalStateException(delegate + " is not an XATopicSession");
    }
    TopicSession xats = ((XATopicSession) delegate).getTopicSession();
    return TracingSession.create(xats, jmsTracing);
  }
}
