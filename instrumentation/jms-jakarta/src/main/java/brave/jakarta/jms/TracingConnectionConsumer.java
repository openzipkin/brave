/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import jakarta.jms.ConnectionConsumer;
import jakarta.jms.JMSException;
import jakarta.jms.ServerSessionPool;

final class TracingConnectionConsumer implements ConnectionConsumer {
  static ConnectionConsumer create(ConnectionConsumer delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("connectionConsumer == null");
    if (delegate instanceof TracingConnectionConsumer) return delegate;
    return new TracingConnectionConsumer(delegate, jmsTracing);
  }

  final ConnectionConsumer delegate;
  final JmsTracing jmsTracing;

  TracingConnectionConsumer(ConnectionConsumer delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
  }

  @Override public ServerSessionPool getServerSessionPool() throws JMSException {
    return TracingServerSessionPool.create(delegate.getServerSessionPool(), jmsTracing);
  }

  @Override public void close() throws JMSException {
    delegate.close();
  }
}
