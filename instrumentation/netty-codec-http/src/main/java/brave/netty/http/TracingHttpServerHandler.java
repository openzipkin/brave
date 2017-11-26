package brave.netty.http;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

final class TracingHttpServerHandler extends ChannelDuplexHandler {
  final Tracer tracer;
  final HttpServerHandler<HttpRequest, HttpResponse> handler;
  final TraceContext.Extractor<HttpHeaders> extractor;

  TracingHttpServerHandler(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(httpTracing, new HttpNettyAdapter());
    extractor = httpTracing.tracing().propagation().extractor(HttpHeaders::get);
  }

  @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (!(msg instanceof HttpRequest)) return;

    HttpRequest httpRequest = (HttpRequest) msg;
    Span span = handler.handleReceive(extractor, httpRequest.headers(), httpRequest);
    ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE).set(span);
    // TODO figure out how to get the client IP from ctx and set it if not span.isNoop

    try (SpanInScope ws = tracer.withSpanInScope(span)) {
      super.channelRead(ctx, msg);
    } catch (Exception | Error e) {
      ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE).set(null);
      handler.handleSend(null, e, span);
      throw e;
    }
  }

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) throws Exception {
    if (!(msg instanceof HttpResponse)) return;

    HttpResponse response = (HttpResponse) msg;
    Span span = ctx.channel().attr(NettyHttpTracing.SPAN_ATTRIBUTE).get();

    Throwable error = null;
    try (SpanInScope ws = tracer.withSpanInScope(span)) {
      super.write(ctx, msg, prm);
    } catch (Exception | Error e) {
      error = e;
      throw e;
    } finally {
      handler.handleSend(response, error, span);
    }
  }
}
