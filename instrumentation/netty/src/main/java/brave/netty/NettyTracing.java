package brave.netty;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

public final class NettyTracing {
  public static NettyTracing create(Tracing tracing) {
    return new NettyTracing(HttpTracing.create(tracing));
  }

  public static NettyTracing create(HttpTracing httpTracing) {
    return new NettyTracing(httpTracing);
  }

  final Tracer tracer;
  final HttpServerHandler<HttpRequest, HttpResponse> handler;
  final TraceContext.Extractor<HttpHeaders> extractor;

  NettyTracing(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpServerHandler.create(new HttpNettyAdapter(), httpTracing.serverParser());
    extractor = httpTracing.tracing().propagation().extractor(HttpHeaders::get);
  }

  public ChannelInboundHandlerAdapter createHttpRequestHandler() {
    return new ChannelInboundHandlerAdapter() {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        HttpRequest httpRequest = (HttpRequest) msg;
        httpRequest = (HttpRequest) msg;
        HttpHeaders headers = httpRequest.headers();
        //貌似是这个
        Span span = tracer.nextSpan(extractor, headers);
        HttpNettyAdapter.parseClientAddress(httpRequest, span);
        handler.handleReceive(httpRequest, span);

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
          super.channelRead(ctx, httpRequest);
        } catch (Exception e) {
          throw e;
        }
      }
    };
  }

  public ChannelOutboundHandlerAdapter createHttpResponseHandler() {

    return new ChannelOutboundHandlerAdapter() {
      @Override
      public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
          throws Exception {
        HttpResponse response = (HttpResponse) msg;
        Span span = tracer.currentSpan();
        if (span == null) return;

        tracer.withSpanInScope(span).close();
        handler.handleSend(response, null, span);

        super.write(ctx, msg, promise);
      }
    };
  }
}
