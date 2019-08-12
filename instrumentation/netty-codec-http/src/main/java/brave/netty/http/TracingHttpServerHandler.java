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
import brave.http.HttpSampler;
import brave.http.HttpServerAdapter;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
import brave.internal.Platform;
import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.net.InetSocketAddress;

final class TracingHttpServerHandler extends ChannelDuplexHandler {
  static final Getter<HttpHeaders, String> GETTER = new Getter<HttpHeaders, String>() {
    @Override public String get(HttpHeaders carrier, String key) {
      return carrier.get(key);
    }

    @Override public String toString() {
      return "HttpHeaders::get";
    }
  };

  final Tracer tracer;
  final HttpNettyAdapter adapter;
  final TraceContext.Extractor<HttpHeaders> extractor;
  final HttpSampler sampler;
  final HttpServerParser parser;

  TracingHttpServerHandler(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    sampler = httpTracing.serverSampler();
    parser = httpTracing.serverParser();
    adapter = new HttpNettyAdapter();
    extractor = httpTracing.tracing().propagation().extractor(GETTER);
  }

  @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof HttpRequest)) {
      ctx.fireChannelRead(msg); // superclass does not throw
      return;
    }

    HttpRequest request = (HttpRequest) msg;

    Span span = nextSpan(extractor.extract(request.headers()), request).kind(Span.Kind.SERVER);
    ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE).set(span);
    SpanInScope spanInScope = tracer.withSpanInScope(span);
    ctx.channel().attr(NettyHttpTracing.SPAN_IN_SCOPE_ATTRIBUTE).set(spanInScope);

    // Place the span in scope so that downstream code can read trace IDs
    try {
      if (!span.isNoop()) {
        parseChannelAddress(ctx, request, span);
        parser.request(adapter, request, span.customizer());
        span.start();
      }
      ctx.fireChannelRead(msg);
      spanInScope.close();
    } catch (RuntimeException | Error e) {
      spanInScope.close();
      span.error(e).finish(); // the request abended, so finish the span;
      throw e;
    }
  }

  /**
   * This sets the client IP:port to the {@linkplain Channel#remoteAddress() remote address} if
   * {@link HttpServerAdapter#parseClientIpAndPort} fails.
   */
  void parseChannelAddress(ChannelHandlerContext ctx, HttpRequest request, Span span) {
    if (adapter.parseClientIpFromXForwardedFor(request, span)) return;
    InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
    if (remoteAddress.getAddress() == null) return;
    span.remoteIpAndPort(Platform.get().getHostString(remoteAddress), remoteAddress.getPort());
  }

  /** Creates a potentially noop span representing this request */
  // copy/pasted from HttpServerHandler.nextSpan
  Span nextSpan(TraceContextOrSamplingFlags extracted, HttpRequest request) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the http sampler made a decision
    if (sampled == null && (sampled = sampler.trySample(adapter, request)) != null) {
      extracted = extracted.sampled(sampled.booleanValue());
    }
    return extracted.context() != null
      ? tracer.joinSpan(extracted.context())
      : tracer.nextSpan(extracted);
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
    try {
      ctx.write(msg, prm);
      parser.response(adapter, response, null, span.customizer());
    } catch (RuntimeException | Error e) {
      span.error(e);
      throw e;
    } finally {
      spanInScope.close(); // clear scope before reporting
      span.finish();
    }
  }
}
