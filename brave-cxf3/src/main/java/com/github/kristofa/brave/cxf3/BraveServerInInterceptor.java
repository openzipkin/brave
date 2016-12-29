package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static com.github.kristofa.brave.cxf3.BraveCxfConstants.BRAVE_SERVER_SPAN;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class BraveServerInInterceptor extends AbstractPhaseInterceptor<Message> {

  /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
  public static BraveServerInInterceptor create(Brave brave) {
    return new Builder(brave).build();
  }

  public static Builder builder(Brave brave) {
    return new Builder(brave);
  }

  public static final class Builder {
    final Brave brave;
    SpanNameProvider spanNameProvider = new DefaultSpanNameProvider();

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
    }

    public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
      this.spanNameProvider = checkNotNull(spanNameProvider, "spanNameProvider");
      return this;
    }

    public BraveServerInInterceptor build() {
      return new BraveServerInInterceptor(this);
    }
  }

  final ServerSpanThreadBinder threadBinder;
  final ServerRequestInterceptor requestInterceptor;
  final SpanNameProvider spanNameProvider;

  BraveServerInInterceptor(Builder b) { // intentionally hidden
    super(Phase.RECEIVE);
    addBefore(StaxInInterceptor.class.getName());
    this.threadBinder = b.brave.serverSpanThreadBinder();
    this.requestInterceptor = b.brave.serverRequestInterceptor();
    this.spanNameProvider = b.spanNameProvider;
  }

  @Override
  public void handleMessage(final Message message) throws Fault {
    try {
      requestInterceptor.handle(
          new HttpServerRequestAdapter(new HttpMessage.ServerRequest(message), spanNameProvider));
      message.getExchange().put(BRAVE_SERVER_SPAN, threadBinder.getCurrentServerSpan());
    } finally {
      threadBinder.setCurrentSpan(ServerSpan.EMPTY);
    }
  }
}