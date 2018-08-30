package brave.jms;

import javax.jms.JMSException;
import javax.jms.ServerSession;
import javax.jms.ServerSessionPool;

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
