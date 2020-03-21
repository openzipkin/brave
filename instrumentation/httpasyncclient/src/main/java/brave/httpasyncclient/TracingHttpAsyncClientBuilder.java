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
package brave.httpasyncclient;

import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.Future;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.StatusLine;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;

/**
 * Note: The current span is only visible to interceptors {@link #addInterceptorLast(HttpRequestInterceptor)
 * added last}.
 */
public final class TracingHttpAsyncClientBuilder extends HttpAsyncClientBuilder {
  public static HttpAsyncClientBuilder create(Tracing tracing) {
    return new TracingHttpAsyncClientBuilder(HttpTracing.create(tracing));
  }

  public static HttpAsyncClientBuilder create(HttpTracing httpTracing) {
    return new TracingHttpAsyncClientBuilder(httpTracing);
  }

  final CurrentTraceContext currentTraceContext;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

  TracingHttpAsyncClientBuilder(HttpTracing httpTracing) { // intentionally hidden
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    this.currentTraceContext = httpTracing.tracing().currentTraceContext();
    this.handler = HttpClientHandler.create(httpTracing);
  }

  @Override public CloseableHttpAsyncClient build() {
    super.addInterceptorFirst(new HandleSend());
    super.addInterceptorLast(new RemoveScope());
    super.addInterceptorLast(new HandleReceive());
    return new TracingHttpAsyncClient(super.build());
  }

  final class HandleSend implements HttpRequestInterceptor {
    @Override public void process(HttpRequest request, HttpContext context) {
      TraceContext parent = (TraceContext) context.getAttribute(TraceContext.class.getName());

      HttpRequestWrapper wrapper = new HttpRequestWrapper(request, context);
      Span span = handler.handleSendWithParent(wrapper, parent);
      parseTargetAddress(wrapper.target, span);

      context.setAttribute(Span.class.getName(), span);
      context.setAttribute(Scope.class.getName(), currentTraceContext.newScope(span.context()));
    }
  }

  static final class RemoveScope implements HttpRequestInterceptor {
    @Override public void process(HttpRequest request, HttpContext context) {
      removeScope(context);
    }
  }

  static void removeScope(HttpContext context) {
    Scope scope = (Scope) context.removeAttribute(Scope.class.getName());
    if (scope == null) return;
    context.removeAttribute(Scope.class.getName());
    scope.close();
  }

  final class HandleReceive implements HttpResponseInterceptor {
    @Override public void process(HttpResponse response, HttpContext context) {
      Span span = (Span) context.removeAttribute(Span.class.getName());
      if (span == null) return;
      handler.handleReceive(new HttpResponseWrapper(response, context), null, span);
    }
  }

  static void parseTargetAddress(HttpHost target, Span span) {
    if (span.isNoop()) return;
    if (target == null) return;
    InetAddress address = target.getAddress();
    if (address != null) {
      if (span.remoteIpAndPort(address.getHostAddress(), target.getPort())) return;
    }
    span.remoteIpAndPort(target.getHostName(), target.getPort());
  }

  final class TracingHttpAsyncClient extends CloseableHttpAsyncClient {
    private final CloseableHttpAsyncClient delegate;

    TracingHttpAsyncClient(CloseableHttpAsyncClient delegate) {
      this.delegate = delegate;
    }

    @Override public <T> Future<T> execute(HttpAsyncRequestProducer requestProducer,
      HttpAsyncResponseConsumer<T> responseConsumer, HttpContext httpContext,
      FutureCallback<T> callback) {

      TraceContext invocationContext = currentTraceContext.get();
      if (invocationContext != null) {
        httpContext.setAttribute(TraceContext.class.getName(), invocationContext);
      }

      return delegate.execute(
        new TracingAsyncRequestProducer(requestProducer, httpContext),
        new TracingAsyncResponseConsumer<>(responseConsumer, httpContext),
        httpContext,
        callback != null && invocationContext != null
          ? new TraceContextFutureCallback<>(callback, currentTraceContext, invocationContext)
          : callback
      );
    }

    @Override public void close() throws IOException {
      delegate.close();
    }

    @Override public boolean isRunning() {
      return delegate.isRunning();
    }

    @Override public void start() {
      delegate.start();
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final class TraceContextFutureCallback<T> implements FutureCallback<T> {
    final FutureCallback<T> delegate;
    final CurrentTraceContext currentTraceContext;
    final TraceContext invocationContext;

    TraceContextFutureCallback(FutureCallback<T> delegate,
      CurrentTraceContext currentTraceContext, TraceContext invocationContext) {
      this.delegate = delegate;
      this.currentTraceContext = currentTraceContext;
      this.invocationContext = invocationContext;
    }

    @Override public void completed(T t) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.completed(t);
      }
    }

    @Override public void failed(Exception e) {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.failed(e);
      }
    }

    @Override public void cancelled() {
      try (Scope scope = currentTraceContext.maybeScope(invocationContext)) {
        delegate.cancelled();
      }
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  final class TracingAsyncRequestProducer implements HttpAsyncRequestProducer {
    final HttpAsyncRequestProducer requestProducer;
    final HttpContext context;

    TracingAsyncRequestProducer(HttpAsyncRequestProducer requestProducer, HttpContext context) {
      this.requestProducer = requestProducer;
      this.context = context;
    }

    @Override public void close() throws IOException {
      requestProducer.close();
    }

    @Override public HttpHost getTarget() {
      return requestProducer.getTarget();
    }

    @Override public HttpRequest generateRequest() throws IOException, HttpException {
      return requestProducer.generateRequest();
    }

    @Override public void produceContent(ContentEncoder encoder, IOControl io) throws IOException {
      requestProducer.produceContent(encoder, io);
    }

    @Override public void requestCompleted(HttpContext context) {
      requestProducer.requestCompleted(context);
    }

    @Override public void failed(Exception ex) {
      removeScope(context);
      Span currentSpan = (Span) context.removeAttribute(Span.class.getName());
      if (currentSpan != null) {
        handler.handleReceive(null, ex, currentSpan);
      }
      requestProducer.failed(ex);
    }

    @Override public boolean isRepeatable() {
      return requestProducer.isRepeatable();
    }

    @Override public void resetRequest() throws IOException {
      requestProducer.resetRequest();
    }
  }

  final class TracingAsyncResponseConsumer<T> implements HttpAsyncResponseConsumer<T> {
    final HttpAsyncResponseConsumer<T> responseConsumer;
    final HttpContext context;

    TracingAsyncResponseConsumer(HttpAsyncResponseConsumer<T> responseConsumer,
      HttpContext context) {
      this.responseConsumer = responseConsumer;
      this.context = context;
    }

    @Override public void responseReceived(HttpResponse response)
      throws IOException, HttpException {
      responseConsumer.responseReceived(response);
    }

    @Override public void consumeContent(ContentDecoder decoder, IOControl ioctrl)
      throws IOException {
      responseConsumer.consumeContent(decoder, ioctrl);
    }

    @Override public void responseCompleted(HttpContext context) {
      responseConsumer.responseCompleted(context);
    }

    @Override public void failed(Exception ex) {
      removeScope(context);
      Span currentSpan = (Span) context.removeAttribute(Span.class.getName());
      if (currentSpan != null) {
        handler.handleReceive(null, ex, currentSpan);
      }
      responseConsumer.failed(ex);
    }

    @Override public Exception getException() {
      return responseConsumer.getException();
    }

    @Override public T getResult() {
      return responseConsumer.getResult();
    }

    @Override public boolean isDone() {
      return responseConsumer.isDone();
    }

    @Override public void close() throws IOException {
      responseConsumer.close();
    }

    @Override public boolean cancel() {
      return responseConsumer.cancel();
    }
  }

  static final class HttpRequestWrapper extends HttpClientRequest {
    final HttpRequest request;
    @Nullable final HttpHost target;

    HttpRequestWrapper(HttpRequest request, HttpContext context) {
      this.request = request;
      this.target = HttpClientContext.adapt(context).getTargetHost();
    }

    @Override public Object unwrap() {
      return request;
    }

    @Override public String method() {
      return request.getRequestLine().getMethod();
    }

    @Override public String path() {
      String result = request.getRequestLine().getUri();
      int queryIndex = result.indexOf('?');
      return queryIndex == -1 ? result : result.substring(0, queryIndex);
    }

    @Override public String url() {
      if (target != null) return target.toURI() + request.getRequestLine().getUri();
      return request.getRequestLine().getUri();
    }

    @Override public String header(String name) {
      Header result = request.getFirstHeader(name);
      return result != null ? result.getValue() : null;
    }

    @Override public void header(String name, String value) {
      request.setHeader(name, value);
    }
  }

  static final class HttpResponseWrapper extends HttpClientResponse {
    @Nullable final HttpRequestWrapper request;
    final HttpResponse response;

    HttpResponseWrapper(HttpResponse response, HttpContext context) {
      HttpRequest request = HttpClientContext.adapt(context).getRequest();
      this.request = request != null ? new HttpRequestWrapper(request, context) : null;
      this.response = response;
    }

    @Override public Object unwrap() {
      return response;
    }

    @Override @Nullable public HttpRequestWrapper request() {
      return request;
    }

    @Override public Throwable error() {
      return null; // HttpAsyncRequestProducer cannot see the response and error at the same time
    }

    @Override public int statusCode() {
      StatusLine statusLine = response.getStatusLine();
      return statusLine != null ? statusLine.getStatusCode() : 0;
    }
  }
}
