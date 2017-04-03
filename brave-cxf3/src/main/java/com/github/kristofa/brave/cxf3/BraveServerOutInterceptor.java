package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static com.github.kristofa.brave.cxf3.BraveCxfConstants.BRAVE_SERVER_SPAN;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * @deprecated This component is deprecated and will no longer be published after Brave 4.1. Please
 * use CXF's <a href="https://cwiki.apache.org/confluence/display/CXF20DOC/Using+OpenZipkin+Brave">built-in
 * Brave tracing integration</a> instead.
 */
@Deprecated
public final class BraveServerOutInterceptor extends AbstractPhaseInterceptor<Message> {

  /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
  public static BraveServerOutInterceptor create(Brave brave) {
    return new Builder(brave).build();
  }

  public static Builder builder(Brave brave) {
    return new Builder(brave);
  }

  public static final class Builder {
    final Brave brave;

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
    }

    public BraveServerOutInterceptor build() {
      return new BraveServerOutInterceptor(this);
    }
  }

  final ServerSpanThreadBinder serverSpanThreadBinder;
  final ServerResponseInterceptor responseInterceptor;

  BraveServerOutInterceptor(Builder b) { // intentionally hidden
    super(Phase.PRE_STREAM);
    addBefore(LoggingOutInterceptor.class.getName());
    this.serverSpanThreadBinder = b.brave.serverSpanThreadBinder();
    this.responseInterceptor = b.brave.serverResponseInterceptor();
  }

  @Override
  public void handleMessage(final Message message) throws Fault {
    ServerSpan serverSpan = (ServerSpan) message.getExchange().get(BRAVE_SERVER_SPAN);
    if (serverSpan != null) {
      serverSpanThreadBinder.setCurrentSpan(serverSpan);
      responseInterceptor.handle(new HttpServerResponseAdapter(new HttpMessage.Response(message)));
    }
  }
}