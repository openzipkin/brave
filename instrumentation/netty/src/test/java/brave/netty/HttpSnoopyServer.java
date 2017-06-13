/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package brave.netty;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin.Span;
import zipkin.reporter.Reporter;

public class HttpSnoopyServer implements Runnable {
  private static final Logger logger = LoggerFactory.getLogger(HttpSnoopyServer.class);

  private final int port;
  private ChannelInitializer<SocketChannel> channelInitializer;
  EventLoopGroup bossGroup = new NioEventLoopGroup();
  EventLoopGroup workerGroup = new NioEventLoopGroup();

  public HttpSnoopyServer(int port, ChannelInitializer<SocketChannel> channelInitializer) {
    this.port = port;
    this.channelInitializer = channelInitializer;
  }

  public void run() {
    try {
      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .childHandler(channelInitializer);

      Channel ch = b.bind(port).sync().channel();
      logger.info("netty httpserver start");
      ch.closeFuture().sync();
    } catch (InterruptedException e) {
      logger.info("netty httpserver interrupted");
    } finally {
      stop();
    }
  }

  public void stop() {
    try {
      if (bossGroup != null) bossGroup.awaitTermination(10, TimeUnit.SECONDS);

      if (workerGroup != null) workerGroup.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      ex.printStackTrace();
    }
  }

  public static void start(int port, HttpTracing httpTracing) throws Exception {
    if (httpTracing == null) {
      Tracing tracing =
          Tracing.newBuilder().sampler(Sampler.ALWAYS_SAMPLE).localServiceName("my-service")
              .reporter(
                  new Reporter<Span>() {
                    @Override public void report(Span span) {
                      logger.info(span.toString());
                    }
                  })
              .build();

      httpTracing = HttpTracing.newBuilder(tracing).build();
    }
    new HttpSnoopyServer(port, new HttpSnoopyServerInitializer(httpTracing)).run();
  }

  public static void main(String[] args) {
    Tracing tracing =
        Tracing.newBuilder().sampler(Sampler.ALWAYS_SAMPLE).localServiceName("my-service")
            .reporter(
                new Reporter<Span>() {
                  @Override public void report(Span span) {
                    logger.info(span.toString());
                  }
                })
            .build();

    HttpTracing httpTracing = HttpTracing.newBuilder(tracing).build();

    new Thread(new HttpSnoopyServer(7654,new HttpSnoopyServerInitializer(httpTracing))).start();
  }
}
