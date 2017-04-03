package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.twitter.zipkin.gen.Span;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static com.github.kristofa.brave.cxf3.BraveCxfConstants.BRAVE_CLIENT_SPAN;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

/**
 * @deprecated This component is deprecated and will no longer be published after Brave 4.1. Please
 * use CXF's <a href="https://cwiki.apache.org/confluence/display/CXF20DOC/Using+OpenZipkin+Brave">built-in
 * Brave tracing integration</a> instead.
 */
@Deprecated
public final class BraveClientInInterceptor extends AbstractPhaseInterceptor<Message> {

  /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
  public static BraveClientInInterceptor create(Brave brave) {
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

    public BraveClientInInterceptor build() {
      return new BraveClientInInterceptor(this);
    }
  }

  final ClientSpanThreadBinder threadBinder;
  final ClientResponseInterceptor responseInterceptor;

  BraveClientInInterceptor(Builder b) { // intentionally hidden
    super(Phase.RECEIVE);
    addBefore(StaxInInterceptor.class.getName());
    this.threadBinder = b.brave.clientSpanThreadBinder();
    this.responseInterceptor = b.brave.clientResponseInterceptor();
  }

  @Override
  public void handleMessage(Message message) throws Fault {
    Span span = (Span) message.getExchange().get(BRAVE_CLIENT_SPAN);
    if (span != null) {
      threadBinder.setCurrentSpan(span);
      responseInterceptor.handle(new HttpClientResponseAdapter(new HttpMessage.Response(message)));
    }
  }
}