package com.github.kristofa.brave.cxf3;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerRequestInterceptor;
import com.github.kristofa.brave.ServerSpanThreadBinder;
import com.github.kristofa.brave.http.HttpServerRequestAdapter;
import com.github.kristofa.brave.http.SpanNameProvider;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

import static com.github.kristofa.brave.cxf3.BraveCxfConstants.BRAVE_SERVER_SPAN;

public abstract class AbstractBraveServerInInterceptor extends AbstractPhaseInterceptor<Message> {

  final ServerSpanThreadBinder threadBinder;
  final ServerRequestInterceptor requestInterceptor;
  final SpanNameProvider spanNameProvider;

  AbstractBraveServerInInterceptor(final Brave brave,
                                   final SpanNameProvider spanNameProvider) {
    super(Phase.UNMARSHAL);
    this.threadBinder = brave.serverSpanThreadBinder();
    this.requestInterceptor = brave.serverRequestInterceptor();
    this.spanNameProvider = spanNameProvider;
  }

  @Override
  public void handleMessage(final Message message) throws Fault {
    try {
      requestInterceptor.handle(
          new HttpServerRequestAdapter(new HttpMessage.ServerRequest(message), spanNameProvider));
      message.getExchange().put(BRAVE_SERVER_SPAN, threadBinder.getCurrentServerSpan());
    } finally {
      if (isAsync(message)) {
        threadBinder.setCurrentSpan(null);
      }
    }
  }

  protected boolean isAsync(final Message message) {
    return !message.getExchange().isSynchronous();
  }
}