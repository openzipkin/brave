package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerResponseAdapter;
import com.github.kristofa.brave.ServerResponseInterceptor;
import com.github.kristofa.brave.ServerSpan;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpServerResponseAdapter;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static com.github.kristofa.brave.cxf3.BraveCxfConstants.BRAVE_SERVER_SPAN;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class BraveServerOutInterceptor extends AbstractPhaseInterceptor<Message> {

  /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
  public static BraveServerOutInterceptor create(Brave brave) {
    return new Builder(brave).build();
  }

  public static Builder builder(Brave brave) {
    return new Builder(brave);
  }

  public static final class Builder implements TagExtractor.Config<Builder> {
    final Brave brave;
    final HttpServerResponseAdapter.FactoryBuilder adapterFactoryBuilder
        = HttpServerResponseAdapter.factoryBuilder();

    Builder(Brave brave) { // intentionally hidden
      this.brave = checkNotNull(brave, "brave");
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

    public BraveServerOutInterceptor build() {
      return new BraveServerOutInterceptor(this);
    }
  }

  final ServerSpanThreadBinder threadBinder;
  final ServerResponseInterceptor interceptor;
  final ServerResponseAdapter.Factory<HttpMessage.Response> adapterFactory;

  BraveServerOutInterceptor(Builder b) { // intentionally hidden
    super(Phase.PRE_STREAM);
    addBefore(LoggingOutInterceptor.class.getName());
    this.threadBinder = b.brave.serverSpanThreadBinder();
    this.interceptor = b.brave.serverResponseInterceptor();
    this.adapterFactory = b.adapterFactoryBuilder.build(HttpMessage.Response.class);
  }

  @Override
  public void handleMessage(final Message message) throws Fault {
    ServerSpan serverSpan = (ServerSpan) message.getExchange().get(BRAVE_SERVER_SPAN);
    if (serverSpan != null) {
      threadBinder.setCurrentSpan(serverSpan);
      ServerResponseAdapter adapter = adapterFactory.create(new HttpMessage.Response(message));
      interceptor.handle(adapter);
    }
  }
}