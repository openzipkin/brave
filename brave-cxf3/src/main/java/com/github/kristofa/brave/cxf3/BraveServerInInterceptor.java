package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.Propagation;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import java.util.List;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static com.github.kristofa.brave.cxf3.BraveCxfConstants.BRAVE_SERVER_SPAN;
import static com.github.kristofa.brave.cxf3.HttpMessage.getHeaders;
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
  final Propagation.Extractor<Message> extractor;
  final ServerRequestInterceptor requestInterceptor;
  final SpanNameProvider spanNameProvider;

  BraveServerInInterceptor(Builder b) { // intentionally hidden
    super(Phase.RECEIVE);
    addBefore(StaxInInterceptor.class.getName());
    this.threadBinder = b.brave.serverSpanThreadBinder();
    this.extractor = b.brave.propagation().extractor((carrier, key) -> {
      List<String> values = getHeaders(carrier).get(key);
      return values != null && !values.isEmpty() ? values.get(0) : null;
    });
    this.requestInterceptor = b.brave.serverRequestInterceptor();
    this.spanNameProvider = b.spanNameProvider;
  }

  @Override
  public void handleMessage(final Message message) throws Fault {
    HttpServerRequestAdapter adapter =
        new HttpServerRequestAdapter(new HttpMessage.ServerRequest(message), spanNameProvider);
    try {
      TraceData traceData = extractor.extractTraceData(message);
      requestInterceptor.internalMaybeTrace(adapter, traceData);
      message.getExchange().put(BRAVE_SERVER_SPAN, threadBinder.getCurrentServerSpan());
    } finally {
      threadBinder.setCurrentSpan(null);
    }
  }
}