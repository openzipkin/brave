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

import brave.test.http.ITHttpServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import java.net.InetSocketAddress;
import org.junit.After;

public class ITNettyHttpTracing extends ITHttpServer {
  EventLoopGroup bossGroup;
  EventLoopGroup workerGroup;
  int port;

  @Override protected void init() throws Exception {
    stop();
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
          p.addLast(NettyHttpTracing.create(httpTracing).serverHandler());
          p.addLast(new TestHandler(httpTracing));
        }
      });

    Channel ch = b.bind(0).sync().channel();
    port = ((InetSocketAddress) ch.localAddress()).getPort();
  }

  @Override
  protected String url(String path) {
    return "http://127.0.0.1:" + port + path;
  }

  @After public void stop() {
    if (bossGroup != null) bossGroup.shutdownGracefully();
    if (workerGroup != null) workerGroup.shutdownGracefully();
  }
}
