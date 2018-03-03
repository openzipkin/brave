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
