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

import brave.Tracing;
import brave.http.HttpServerBenchmarks;
import brave.propagation.B3Propagation;
import brave.propagation.ExtraFieldPropagation;
import brave.sampler.Sampler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.AttributeKey;
import io.undertow.servlet.api.DeploymentInfo;
import java.net.InetSocketAddress;
import java.util.Arrays;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import zipkin2.reporter.Reporter;

public class NettyHttpServerBenchmarks extends HttpServerBenchmarks {

  EventLoopGroup bossGroup;
  EventLoopGroup workerGroup;

  @Override protected void init(DeploymentInfo servletBuilder) {
  }

  // NOTE: if the tracing server handler starts to override more methods, this needs to be updated
  static class TracingDispatchHandler extends ChannelDuplexHandler {
    static final AttributeKey<String> URI_ATTRIBUTE = AttributeKey.valueOf("uri");

    final ChannelDuplexHandler unsampled = NettyHttpTracing.create(
      Tracing.newBuilder().sampler(Sampler.NEVER_SAMPLE).spanReporter(Reporter.NOOP).build()
    ).serverHandler();
    final ChannelDuplexHandler traced = NettyHttpTracing.create(
      Tracing.newBuilder()
        .propagationFactory(ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
          .addField("x-vcap-request-id")
          .addPrefixedFields("baggage-", Arrays.asList("country-code", "user-id"))
          .build()
        )
        .spanReporter(Reporter.NOOP)
        .build()
    ).serverHandler();
    final ChannelDuplexHandler tracedExtra = NettyHttpTracing.create(
      Tracing.newBuilder().spanReporter(Reporter.NOOP).build()
    ).serverHandler();
    final ChannelDuplexHandler traced128 = NettyHttpTracing.create(
      Tracing.newBuilder().traceId128Bit(true).spanReporter(Reporter.NOOP).build()
    ).serverHandler();

    @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (!(msg instanceof HttpRequest)) {
        ctx.fireChannelRead(msg);
        return;
      }
      String uri = ((HttpRequest) msg).uri();
      if ("/unsampled".equals(uri)) {
        ctx.channel().attr(URI_ATTRIBUTE).set(uri);
        unsampled.channelRead(ctx, msg);
      } else if ("/traced".equals(uri)) {
        ctx.channel().attr(URI_ATTRIBUTE).set(uri);
        traced.channelRead(ctx, msg);
      } else if ("/tracedextra".equals(uri)) {
        ctx.channel().attr(URI_ATTRIBUTE).set(uri);
        ExtraFieldPropagation.set("country-code", "FO");
        tracedExtra.channelRead(ctx, msg);
      } else if ("/traced128".equals(uri)) {
        ctx.channel().attr(URI_ATTRIBUTE).set(uri);
        traced128.channelRead(ctx, msg);
      } else {
        ctx.fireChannelRead(msg);
      }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) throws Exception {
      String uri = ctx.channel().attr(URI_ATTRIBUTE).get();
      if (uri == null) {
        ctx.write(msg, prm);
        return;
      }
      if ("/unsampled".equals(uri)) {
        unsampled.write(ctx, msg, prm);
      } else if ("/traced".equals(uri)) {
        traced.write(ctx, msg, prm);
      } else if ("/traced128".equals(uri)) {
        traced128.write(ctx, msg, prm);
      } else {
        ctx.write(msg, prm);
      }
    }
  }

  @Override protected int initServer() throws InterruptedException {
    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup();

    ServerBootstrap b = new ServerBootstrap();
    b.option(ChannelOption.SO_BACKLOG, 1024);
    b.group(bossGroup, workerGroup)
      .channel(NioServerSocketChannel.class)
      .childHandler(new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(final Channel ch) throws Exception {
          ChannelPipeline p = ch.pipeline();
          p.addLast(new HttpServerCodec());
          p.addLast(new TracingDispatchHandler());
          p.addLast(new HelloWorldHandler());
        }
      });

    Channel ch = b.bind(0).sync().channel();
    return ((InetSocketAddress) ch.localAddress()).getPort();
  }

  @TearDown(Level.Trial) public void closeNetty() throws Exception {
    if (bossGroup != null) bossGroup.shutdownGracefully();
    if (workerGroup != null) workerGroup.shutdownGracefully();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException, InterruptedException {
    //System.out.println(new NettyHttpServerBenchmarks().initServer());
    //
    //Thread.sleep(10 * 1000 * 60);
    Options opt = new OptionsBuilder()
      .include(".*" + NettyHttpServerBenchmarks.class.getSimpleName() + ".*")
      .build();

    new Runner(opt).run();
  }
}
