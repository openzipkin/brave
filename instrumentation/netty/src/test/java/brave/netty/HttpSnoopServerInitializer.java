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

import brave.http.HttpTracing;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

public class HttpSnoopServerInitializer extends ChannelInitializer<SocketChannel> {
  private HttpTracing httpTracing;

  public HttpSnoopServerInitializer(HttpTracing httpTracing) {
    this.httpTracing = httpTracing;
  }

  @Override
  public void initChannel(SocketChannel ch) throws Exception {
    ChannelPipeline p = ch.pipeline();

    p.addLast("decoder", new HttpRequestDecoder());
    p.addLast("encoder", new HttpResponseEncoder());
    p.addLast("aggregator", new HttpObjectAggregator(1048576));

    p.addLast("braveResponse", NettyTracing.create(httpTracing).createHttpResponseHandler());

    p.addLast("braveRequest", NettyTracing.create(httpTracing).createHttpRequestHandler());

    p.addLast("handler", new HttpSnoopServerHandler(httpTracing));
    //p.addLast("exception", new ExceptionHandler());//Make sure this is the last line when init the pipeline.

  }
}
