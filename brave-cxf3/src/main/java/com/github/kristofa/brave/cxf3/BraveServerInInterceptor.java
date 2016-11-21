package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestAdapter;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.TagExtractor;
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

  public static final class Builder implements TagExtractor.Config<Builder> {
    final Brave brave;
    final HttpServerRequestAdapter.FactoryBuilder adapterFactoryBuilder
        = HttpServerRequestAdapter.factoryBuilder();

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
    }

    public Builder spanNameProvider(SpanNameProvider spanNameProvider) {
      adapterFactoryBuilder.spanNameProvider(spanNameProvider);
      return this;
    }

    @Override public Builder addKey(String key) {
      adapterFactoryBuilder.addKey(key);
      return this;
    }

    @Override
    public Builder addValueParserFactory(TagExtractor.ValueParserFactory factory) {
      adapterFactoryBuilder.addValueParserFactory(factory);
      return this;
    }

    public BraveServerInInterceptor build() {
      return new BraveServerInInterceptor(this);
    }
  }

  final ServerSpanThreadBinder threadBinder;
  final ServerRequestInterceptor interceptor;
  final ServerRequestAdapter.Factory<HttpMessage.ServerRequest> adapterFactory;

  BraveServerInInterceptor(Builder b) { // intentionally hidden
    super(Phase.RECEIVE);
    addBefore(StaxInInterceptor.class.getName());
    this.threadBinder = b.brave.serverSpanThreadBinder();
    this.interceptor = b.brave.serverRequestInterceptor();
    this.adapterFactory = b.adapterFactoryBuilder.build(HttpMessage.ServerRequest.class);
  }

  @Override
  public void handleMessage(final Message message) throws Fault {
    try {
      ServerRequestAdapter adapter = adapterFactory.create(new HttpMessage.ServerRequest(message));
      interceptor.handle(adapter);
      message.getExchange().put(BRAVE_SERVER_SPAN, threadBinder.getCurrentServerSpan());
    } finally {
      threadBinder.setCurrentSpan(null);
    }
  }
}