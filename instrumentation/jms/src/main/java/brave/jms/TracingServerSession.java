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

import javax.jms.JMSException;
import javax.jms.ServerSession;
import javax.jms.Session;

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
