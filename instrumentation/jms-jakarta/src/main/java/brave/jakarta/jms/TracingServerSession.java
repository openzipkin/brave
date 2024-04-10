/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import jakarta.jms.JMSException;
import jakarta.jms.ServerSession;
import jakarta.jms.Session;

final class TracingServerSession implements ServerSession {
  static ServerSession create(ServerSession delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("serverSession == null");
    if (delegate instanceof TracingServerSession) return delegate;
    return new TracingServerSession(delegate, jmsTracing);
  }

  final ServerSession delegate;
  final JmsTracing jmsTracing;

  TracingServerSession(ServerSession delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
  }

  @Override public Session getSession() throws JMSException {
    return TracingSession.create(delegate.getSession(), jmsTracing);
  }

  @Override public void start() throws JMSException {
    delegate.start();
  }
}
