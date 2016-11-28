package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ClientRequestAdapter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientSpanThreadBinder;
import com.github.kristofa.brave.TagExtractor;
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

  public static final class Builder implements TagExtractor.Config<Builder> {
    final Brave brave;
    final HttpClientRequestAdapter.FactoryBuilder adapterFactoryBuilder
        = HttpClientRequestAdapter.factoryBuilder();

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

    public BraveClientOutInterceptor build() {
      return new BraveClientOutInterceptor(this);
    }
  }

  final ClientSpanThreadBinder threadBinder;
  final ClientRequestInterceptor interceptor;
  final ClientRequestAdapter.Factory<HttpMessage.ClientRequest> adapterFactory;

  BraveClientOutInterceptor(Builder b) { // intentionally hidden
    super(Phase.PRE_STREAM);
    addBefore(LoggingOutInterceptor.class.getName());
    this.threadBinder = b.brave.clientSpanThreadBinder();
    this.interceptor = b.brave.clientRequestInterceptor();
    this.adapterFactory = b.adapterFactoryBuilder.build(HttpMessage.ClientRequest.class);
  }

  @Override
  public void handleMessage(Message message) throws Fault {
    try {
      ClientRequestAdapter adapter = adapterFactory.create(new HttpMessage.ClientRequest(message));
      interceptor.handle(adapter);
      message.getExchange().put(BRAVE_CLIENT_SPAN, threadBinder.getCurrentClientSpan());
    } finally {
      threadBinder.setCurrentSpan(null);
    }
  }
}
