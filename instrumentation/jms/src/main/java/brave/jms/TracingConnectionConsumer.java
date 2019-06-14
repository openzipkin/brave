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

import javax.jms.ConnectionConsumer;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;

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
