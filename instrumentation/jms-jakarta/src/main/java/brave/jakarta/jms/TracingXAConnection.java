/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import jakarta.jms.JMSException;
import jakarta.jms.XAConnection;
import jakarta.jms.XAQueueConnection;
import jakarta.jms.XAQueueSession;
import jakarta.jms.XASession;
import jakarta.jms.XATopicConnection;
import jakarta.jms.XATopicSession;

/** Implements all interfaces as according to ActiveMQ, this is typical of JMS 1.1. */
final class TracingXAConnection extends TracingConnection
  implements XATopicConnection, XAQueueConnection {

  static TracingXAConnection create(XAConnection delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXAConnection) return (TracingXAConnection) delegate;
    return new TracingXAConnection(delegate, jmsTracing);
  }

  TracingXAConnection(XAConnection delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public XASession createXASession() throws JMSException {
    return TracingXASession.create(((XAConnection) delegate).createXASession(), jmsTracing);
  }

  @Override public XAQueueSession createXAQueueSession() throws JMSException {
    if ((types & TYPE_XA_QUEUE) != TYPE_XA_QUEUE) {
      throw new IllegalStateException(delegate + " is not an XAQueueConnection");
    }
    XAQueueSession xats = ((XAQueueConnection) delegate).createXAQueueSession();
    return TracingXASession.create(xats, jmsTracing);
  }

  @Override public XATopicSession createXATopicSession() throws JMSException {
    if ((types & TYPE_XA_TOPIC) != TYPE_XA_TOPIC) {
      throw new IllegalStateException(delegate + " is not an XATopicConnection");
    }
    XATopicSession xats = ((XATopicConnection) delegate).createXATopicSession();
    return TracingXASession.create(xats, jmsTracing);
  }
}

