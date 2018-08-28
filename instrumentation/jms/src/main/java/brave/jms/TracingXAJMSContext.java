package brave.jms;

import javax.jms.JMSContext;
import javax.jms.XAJMSContext;
import javax.transaction.xa.XAResource;

final class TracingXAJMSContext extends TracingJMSContext implements XAJMSContext {

  TracingXAJMSContext(XAJMSContext delegate, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
  }

  @Override public JMSContext getContext() {
    return this;
  }

  @Override public XAResource getXAResource() {
    return ((XAJMSContext)delegate).getXAResource();
  }
}