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
package brave.netty.http;

import brave.Span;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.internal.Platform;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.Attribute;
import java.net.InetSocketAddress;
import java.net.URI;

final class TracingHttpServerHandler extends ChannelDuplexHandler {
  final CurrentTraceContext currentTraceContext;
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> handler;

  TracingHttpServerHandler(HttpTracing httpTracing) {
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpServerHandler.create(httpTracing);
  }

  @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof HttpRequest)) {
      ctx.fireChannelRead(msg); // superclass does not throw
      return;
    }

    HttpRequestWrapper request =
      new HttpRequestWrapper((HttpRequest) msg, (InetSocketAddress) ctx.channel().remoteAddress());

    ctx.channel().attr(NettyHttpTracing.REQUEST_ATTRIBUTE).set(request);
    Span span = handler.handleReceive(request);
    ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE).set(span);
    Scope scope = currentTraceContext.newScope(span.context());

    // Place the span in scope so that downstream code can read trace IDs
    Throwable error = null;
    try {
      ctx.fireChannelRead(msg);
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      if (error != null) span.error(error).finish();
      scope.close();
    }
  }

  @Override public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Attribute<Span> spanAttr = ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE);
    Span span = spanAttr.get();
    spanAttr.compareAndSet(span, null);
    if (span == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    HttpResponse response = (HttpResponse) msg;

    Scope scope = currentTraceContext.maybeScope(span.context());
    Throwable error = null;
    try {
      ctx.write(msg, prm);
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      HttpServerRequest request = ctx.channel().attr(NettyHttpTracing.REQUEST_ATTRIBUTE).get();
      handler.handleSend(new HttpResponseWrapper(request, response, error), span);
      scope.close();
    }
  }

  static final class HttpRequestWrapper extends HttpServerRequest {
    final HttpRequest request;
    @Nullable final InetSocketAddress remoteAddress;

    HttpRequestWrapper(HttpRequest request, InetSocketAddress remoteAddress) {
      this.request = request;
      this.remoteAddress = remoteAddress;
    }

    @Override public HttpRequest unwrap() {
      return request;
    }

    @Override public boolean parseClientIpAndPort(Span span) {
      if (parseClientIpFromXForwardedFor(span)) return true;
      if (remoteAddress == null || remoteAddress.getAddress() == null) return false;
      return span.remoteIpAndPort(
        Platform.get().getHostString(remoteAddress),
        remoteAddress.getPort()
      );
    }

    @Override public String method() {
      return request.method().name();
    }

    @Override public String path() {
      return URI.create(request.uri()).getPath(); // TODO benchmark
    }

    @Override public String url() {
      String host = header("Host");
      if (host == null) return null;
      // TODO: we don't know if this is really http or https!
      return "http://" + host + request.uri();
    }

    @Override public String header(String name) {
      return request.headers().get(name);
    }
  }

  static final class HttpResponseWrapper extends HttpServerResponse {
    @Nullable final HttpServerRequest request;
    final HttpResponse delegate;
    @Nullable final Throwable error;

    HttpResponseWrapper(
      @Nullable HttpServerRequest request,
      HttpResponse response,
      @Nullable Throwable error
    ) {
      this.request = request;
      this.delegate = response;
      this.error = error;
    }

    @Override public HttpResponse unwrap() {
      return delegate;
    }

    @Override @Nullable public HttpServerRequest request() {
      return request;
    }

    @Override public Throwable error() {
      return error;
    }

    @Override public int statusCode() {
      HttpResponseStatus status = delegate.status();
      return status != null ? status.code() : 0;
    }
  }
}
