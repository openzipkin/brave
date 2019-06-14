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

import javax.jms.JMSContext;
import javax.jms.XAJMSContext;
import javax.transaction.xa.XAResource;

@JMS2_0 final class TracingXAJMSContext extends TracingJMSContext implements XAJMSContext {
  static XAJMSContext create(XAJMSContext delegate, JmsTracing jmsTracing) {
    if (delegate instanceof TracingXAJMSContext) return delegate;
    return new TracingXAJMSContext(delegate, jmsTracing);
  }

  TracingXAJMSContext(XAJMSContext delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public JMSContext getContext() {
    return this;
  }

  @Override public XAResource getXAResource() {
    return ((XAJMSContext) delegate).getXAResource();
  }
}
