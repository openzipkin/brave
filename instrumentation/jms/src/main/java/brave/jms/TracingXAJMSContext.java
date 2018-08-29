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
