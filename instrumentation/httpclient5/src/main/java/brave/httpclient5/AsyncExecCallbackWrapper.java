package brave.httpclient5;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import java.io.IOException;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;

class AsyncExecCallbackWrapper implements AsyncExecCallback {

  private final AsyncExecCallback asyncExecCallback;
  private final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;
  private final Span span;
  private final HttpRequestWrapper requestWrapper;
  private final HttpClientContext context;

  AsyncExecCallbackWrapper(
    final AsyncExecCallback asyncExecCallback, HttpRequestWrapper requestWrapper,
    HttpClientHandler<HttpClientRequest, HttpClientResponse> handler,
    Span span, HttpClientContext context) {
    this.asyncExecCallback = asyncExecCallback;
    this.requestWrapper = requestWrapper;
    this.handler = handler;
    this.span = span;
    this.context = context;
  }

  @Override
  public AsyncDataConsumer handleResponse(
    final HttpResponse response,
    final EntityDetails entityDetails) throws HttpException, IOException {
    handleSpan(response);
    return asyncExecCallback.handleResponse(response, entityDetails);
  }

  @Override
  public void handleInformationResponse(final HttpResponse response)
    throws HttpException, IOException {
    handleSpan(response);
    asyncExecCallback.handleInformationResponse(response);
  }

  @Override
  public void completed() {
    asyncExecCallback.completed();
  }

  @Override
  public void failed(final Exception cause) {
    handler.handleReceive(new HttpResponseWrapper(null, requestWrapper, cause), span);
    asyncExecCallback.failed(cause);
    // Handle scope if exception is raised after receiving.
    HttpClientUtils.closeScope(context);
  }

  private void handleSpan(HttpResponse response) {
    context.removeAttribute(Span.class.getName());
    if (HttpClientUtils.isLocalCached(context, span)) {
      span.kind(null);
    }
    handler.handleReceive(new HttpResponseWrapper(response, requestWrapper, null), span);
  }
}
