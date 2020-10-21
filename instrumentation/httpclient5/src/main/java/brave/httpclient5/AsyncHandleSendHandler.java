package brave.httpclient5;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.io.IOException;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;

import static brave.httpclient5.HttpClientUtils.parseTargetAddress;

class AsyncHandleSendHandler implements AsyncExecChainHandler {
  private final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;
  @Nullable
  private final String serverName;

  AsyncHandleSendHandler(HttpTracing httpTracing) {
    this.serverName = "".equals(httpTracing.serverName()) ? null : httpTracing.serverName();
    this.handler = HttpClientHandler.create(httpTracing);
  }

  @Override
  public void execute(HttpRequest request, AsyncEntityProducer entityProducer,
    AsyncExecChain.Scope scope,
    AsyncExecChain chain, AsyncExecCallback asyncExecCallback)
    throws HttpException, IOException {
    HttpClientContext context = scope.clientContext;
    TraceContext parent = (TraceContext) context.getAttribute(TraceContext.class.getName());

    HttpRequestWrapper requestWrapper =
      new HttpRequestWrapper(request, scope.route.getTargetHost());
    Span span = handler.handleSendWithParent(requestWrapper, parent);
    context.setAttribute(Span.class.getName(), span);

    if (serverName != null) {
      span.remoteServiceName(serverName);
    }
    parseTargetAddress(requestWrapper.target, span);
    AsyncExecCallbackWrapper callbackWrapper =
      new AsyncExecCallbackWrapper(asyncExecCallback, requestWrapper, handler, span, context);
    try {
      chain.proceed(request, entityProducer, scope, callbackWrapper);
    } catch (Throwable e) {
      // Handle if exception is raised before sending.
      context.removeAttribute(Span.class.getName());
      HttpClientUtils.closeScope(context);
      handler.handleReceive(new HttpResponseWrapper(null, requestWrapper, e), span);
      throw e;
    }
  }
}
