package brave.netty.http;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpSampler;
import brave.http.HttpServerHandler;
import brave.http.HttpServerParser;
import brave.http.HttpTracing;
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
import zipkin2.Endpoint;

final class TracingHttpServerHandler extends ChannelDuplexHandler {
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
    extractor = httpTracing.tracing().propagation().extractor(HttpHeaders::get);
  }

  @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof HttpRequest)) {
      ctx.fireChannelRead(msg); // superclass does not throw
      return;
    }

    HttpRequest request = (HttpRequest) msg;

    Span span = nextSpan(extractor.extract(request.headers()), request).kind(Span.Kind.SERVER);
    ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE).set(span);

    // Place the span in scope so that downstream code can read trace IDs
    try (SpanInScope ws = tracer.withSpanInScope(span)) {
      if (!span.isNoop()) {
        parser.request(adapter, request, span);
        maybeParseClientAddress(ctx.channel(), request, span);
        span.start();
      }
      ctx.fireChannelRead(msg);
    }
  }

  /** Like {@link HttpServerHandler}, but accepts a channel */
  void maybeParseClientAddress(Channel channel, HttpRequest request, Span span) {
    Endpoint.Builder remoteEndpoint = Endpoint.newBuilder();
    if (adapter.parseClientAddress(request, remoteEndpoint)) {
      span.remoteEndpoint(remoteEndpoint.build());
    } else {
      InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();
      span.remoteEndpoint(Endpoint.newBuilder()
          .ip(remoteAddress.getAddress())
          .port(remoteAddress.getPort())
          .build());
    }
  }

  /** Creates a potentially noop span representing this request */
  // copy/pasted from HttpServerHandler.nextSpan
  Span nextSpan(TraceContextOrSamplingFlags extracted, HttpRequest request) {
    if (extracted.sampled() == null) { // Otherwise, try to make a new decision
      extracted = extracted.sampled(sampler.trySample(adapter, request));
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

    // Place the span in scope so that downstream code can read trace IDs
    HttpResponse response = (HttpResponse) msg;
    try (SpanInScope ws = tracer.withSpanInScope(span)) {
      ctx.write(msg, prm);
      parser.response(adapter, response, null, span);
      span.finish();
    }
  }
}
