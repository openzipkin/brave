/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

import static brave.internal.Throwables.propagateIfFatal;

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

    Scope scope = currentTraceContext.maybeScope(span.context());
    Throwable error = null;
    try {
      ListenableFuture<ClientHttpResponse> result = execution.executeAsync(req, body);
      result.addCallback(new TraceListenableFutureCallback(request, span, handler));
      return invocationContext != null
        ? new TraceContextListenableFuture<ClientHttpResponse>(result, currentTraceContext, invocationContext)
        : result;
    } catch (RuntimeException e) {
      error = e;
      throw e;
    } catch (IOException e) {
      error = e;
      throw e;
    } catch (Error e) {
      propagateIfFatal(e);
      error = e;
      throw e;
    } finally {
      if (error != null) {
        handler.handleReceive(new ClientHttpResponseWrapper(request, null, error), span);
      }
      scope.close();
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
      handler.handleReceive(new ClientHttpResponseWrapper(request, null, ex), span);
    }

    @Override public void onSuccess(ClientHttpResponse response) {
      handler.handleReceive(new ClientHttpResponseWrapper(request, response, null), span);
    }
  }
}
