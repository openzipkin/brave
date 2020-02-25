/*
 * Copyright 2013-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.spring.web;

import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import brave.spring.web.TracingClientHttpRequestInterceptor.ClientHttpResponseWrapper;
import brave.spring.web.TracingClientHttpRequestInterceptor.HttpRequestWrapper;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.AsyncClientHttpRequestExecution;
import org.springframework.http.client.AsyncClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

public final class TracingAsyncClientHttpRequestInterceptor
  implements AsyncClientHttpRequestInterceptor {

  public static AsyncClientHttpRequestInterceptor create(Tracing tracing) {
    return create(HttpTracing.create(tracing));
  }

  public static AsyncClientHttpRequestInterceptor create(HttpTracing httpTracing) {
    return new TracingAsyncClientHttpRequestInterceptor(httpTracing);
  }

  final CurrentTraceContext currentTraceContext;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

  @Autowired TracingAsyncClientHttpRequestInterceptor(HttpTracing httpTracing) {
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpClientHandler.create(httpTracing);
  }

  @Override public ListenableFuture<ClientHttpResponse> intercept(HttpRequest req,
    byte[] body, AsyncClientHttpRequestExecution execution) throws IOException {
    HttpRequestWrapper request = new HttpRequestWrapper(req);
    Span span = handler.handleSend(request);

    // avoid context sync overhead when we are the root span
    TraceContext invocationContext = span.context().parentIdAsLong() != 0
      ? currentTraceContext.get()
      : null;

    try (Scope ws = currentTraceContext.maybeScope(span.context())) {
      ListenableFuture<ClientHttpResponse> result = execution.executeAsync(req, body);
      result.addCallback(new TraceListenableFutureCallback(request, span, handler));
      return invocationContext != null
        ? new TraceContextListenableFuture<>(result, currentTraceContext, invocationContext)
        : result;
    } catch (Throwable e) {
      handler.handleReceive(null, e, span);
      throw e;
    }
  }

  static final class TraceListenableFutureCallback
    implements ListenableFutureCallback<ClientHttpResponse> {
    final HttpRequestWrapper request;
    final Span span;
    final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

    TraceListenableFutureCallback(
      HttpRequestWrapper request, Span span,
      HttpClientHandler<HttpClientRequest, HttpClientResponse> handler) {
      this.request = request;
      this.span = span;
      this.handler = handler;
    }

    @Override public void onFailure(Throwable ex) {
      handler.handleReceive(null, ex, span);
    }

    @Override public void onSuccess(ClientHttpResponse result) {
      handler.handleReceive(new ClientHttpResponseWrapper(request, result, null), null, span);
    }
  }
}
