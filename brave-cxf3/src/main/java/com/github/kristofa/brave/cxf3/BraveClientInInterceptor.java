package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientResponseAdapter;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.TagExtractor;
import com.github.kristofa.brave.http.HttpClientResponseAdapter;
import com.twitter.zipkin.gen.Span;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.StaxInInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static com.github.kristofa.brave.cxf3.BraveCxfConstants.BRAVE_CLIENT_SPAN;
import static com.github.kristofa.brave.internal.Util.checkNotNull;

public final class BraveClientInInterceptor extends AbstractPhaseInterceptor<Message> {

  /** Creates a tracing interceptor with defaults. Use {@link #builder(Brave)} to customize. */
  public static BraveClientInInterceptor create(Brave brave) {
    return new Builder(brave).build();
  }

  public static Builder builder(Brave brave) {
    return new Builder(brave);
  }

  public static final class Builder implements TagExtractor.Config<Builder> {
    final Brave brave;
    final HttpClientResponseAdapter.FactoryBuilder adapterFactoryBuilder
        = HttpClientResponseAdapter.factoryBuilder();

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

    public BraveClientInInterceptor build() {
      return new BraveClientInInterceptor(this);
    }
  }

  final ClientSpanThreadBinder threadBinder;
  final ClientResponseInterceptor interceptor;
  final ClientResponseAdapter.Factory<HttpMessage.Response> adapterFactory;

  BraveClientInInterceptor(Builder b) { // intentionally hidden
    super(Phase.RECEIVE);
    addBefore(StaxInInterceptor.class.getName());
    this.threadBinder = b.brave.clientSpanThreadBinder();
    this.interceptor = b.brave.clientResponseInterceptor();
    this.adapterFactory = b.adapterFactoryBuilder.build(HttpMessage.Response.class);
  }

  @Override
  public void handleMessage(Message message) throws Fault {
    Span span = (Span) message.getExchange().get(BRAVE_CLIENT_SPAN);
    if (span != null) {
      threadBinder.setCurrentSpan(span);
      ClientResponseAdapter adapter = adapterFactory.create(new HttpMessage.Response(message));
      interceptor.handle(adapter);
    }
  }
}