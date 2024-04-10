/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import jakarta.jms.JMSException;
import jakarta.jms.ServerSession;
import jakarta.jms.ServerSessionPool;

final class TracingServerSessionPool implements ServerSessionPool {
  static ServerSessionPool create(ServerSessionPool delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("serverSessionPool == null");
    if (delegate instanceof TracingServerSessionPool) return delegate;
    return new TracingServerSessionPool(delegate, jmsTracing);
  }

  final ServerSessionPool delegate;
  final JmsTracing jmsTracing;

  TracingServerSessionPool(ServerSessionPool delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
  }

  @Override public ServerSession getServerSession() throws JMSException {
    return TracingServerSession.create(delegate.getServerSession(), jmsTracing);
  }
}
