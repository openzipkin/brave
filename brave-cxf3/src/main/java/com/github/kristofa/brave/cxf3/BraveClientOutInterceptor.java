package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpClientRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static com.github.kristofa.brave.cxf3.BraveCxfConstants.BRAVE_CLIENT_SPAN;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class BraveClientOutInterceptor extends AbstractPhaseInterceptor<Message> {

  /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
  public static BraveClientOutInterceptor create(Brave brave) {
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

    public BraveClientOutInterceptor build() {
      return new BraveClientOutInterceptor(this);
    }
  }

  final ClientSpanThreadBinder threadBinder;
  final ClientRequestInterceptor requestInterceptor;
  final SpanNameProvider spanNameProvider;

  BraveClientOutInterceptor(Builder b) { // intentionally hidden
    super(Phase.PRE_STREAM);
    addBefore(LoggingOutInterceptor.class.getName());
    this.threadBinder = b.brave.clientSpanThreadBinder();
    this.requestInterceptor = b.brave.clientRequestInterceptor();
    this.spanNameProvider = b.spanNameProvider;
  }

  @Override
  public void handleMessage(Message message) throws Fault {
    try {
      requestInterceptor.handle(
          new HttpClientRequestAdapter(new HttpMessage.ClientRequest(message), spanNameProvider));
      message.getExchange().put(BRAVE_CLIENT_SPAN, threadBinder.getCurrentClientSpan());
    } finally {
      threadBinder.setCurrentSpan(null);
    }
  }
}
