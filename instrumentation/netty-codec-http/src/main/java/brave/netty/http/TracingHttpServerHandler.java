/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.internal.Platform;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.InetSocketAddress;
import java.net.URI;

final class TracingHttpServerHandler extends ChannelDuplexHandler {
  final HttpServerHandler<brave.http.HttpServerRequest, brave.http.HttpServerResponse> handler;
  final Tracer tracer;

  TracingHttpServerHandler(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing);
  }

  @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof HttpRequest)) {
      ctx.fireChannelRead(msg); // superclass does not throw
      return;
    }

    HttpServerRequest request =
      new HttpServerRequest((HttpRequest) msg, (InetSocketAddress) ctx.channel().remoteAddress());

    Span span = handler.handleReceive(request);
    ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE).set(span);
    SpanInScope spanInScope = tracer.withSpanInScope(span);
    ctx.channel().attr(NettyHttpTracing.SPAN_IN_SCOPE_ATTRIBUTE).set(spanInScope);

    // Place the span in scope so that downstream code can read trace IDs
    try {
      ctx.fireChannelRead(msg);
      spanInScope.close();
    } catch (RuntimeException | Error e) {
      spanInScope.close();
      span.error(e).finish(); // the request abended, so finish the span;
      throw e;
    }
  }

  @Override public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    Span span = ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE).get();
    if (span == null || !(msg instanceof HttpResponse)) {
      ctx.write(msg, prm);
      return;
    }

    HttpResponse response = (HttpResponse) msg;

    // Guard re-scoping the same span
    SpanInScope spanInScope = ctx.channel().attr(NettyHttpTracing.SPAN_IN_SCOPE_ATTRIBUTE).get();
    if (spanInScope == null) spanInScope = tracer.withSpanInScope(span);
    Throwable t = null;
    try {
      ctx.write(msg, prm);
    } catch (RuntimeException | Error e) {
      t = e;
      throw e;
    } finally {
      spanInScope.close(); // clear scope before reporting
      handler.handleSend(new HttpServerResponse(response), t, span);
    }
  }

  static final class HttpServerRequest extends brave.http.HttpServerRequest {
    final HttpRequest request;
    @Nullable final InetSocketAddress remoteAddress;

    HttpServerRequest(HttpRequest request, InetSocketAddress remoteAddress) {
      this.request = request;
      this.remoteAddress = remoteAddress;
    }

    @Override public HttpRequest unwrap() {
      return request;
    }

    @Override public boolean parseClientIpAndPort(Span span) {
      if (remoteAddress.getAddress() == null) return false;
      return span.remoteIpAndPort(Platform.get().getHostString(remoteAddress),
        remoteAddress.getPort());
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

  static final class HttpServerResponse extends brave.http.HttpServerResponse {
    final HttpResponse delegate;

    HttpServerResponse(HttpResponse delegate) {
      this.delegate = delegate;
    }

    @Override public HttpResponse unwrap() {
      return delegate;
    }

    @Override public int statusCode() {
      HttpResponseStatus status = delegate.status();
      return status != null ? status.code() : 0;
    }
  }
}
