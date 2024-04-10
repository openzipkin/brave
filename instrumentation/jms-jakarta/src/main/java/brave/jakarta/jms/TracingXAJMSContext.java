/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.jms;

import jakarta.jms.JMSContext;
import jakarta.jms.XAJMSContext;
import javax.transaction.xa.XAResource;

final class TracingXAJMSContext extends TracingJMSContext implements XAJMSContext {
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
