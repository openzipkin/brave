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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

final class HelloWorldHandler extends ChannelInboundHandlerAdapter {
  static final byte[] HELLO_WORLD = "hello world".getBytes(StandardCharsets.UTF_8);

  @Override public void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof HttpRequest)) return;
    HttpRequest req = (HttpRequest) msg;

    if (HttpUtil.is100ContinueExpected(req)) {
      ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
    }
    boolean keepAlive = HttpUtil.isKeepAlive(req);
    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK,
      Unpooled.wrappedBuffer(HELLO_WORLD));
    response.headers().set(CONTENT_TYPE, "text/plain");
    response.headers().set(CONTENT_LENGTH, response.content().readableBytes());

    if (!keepAlive) {
      ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    } else {
      response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      ctx.write(response);
    }
  }

  @Override public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    cause.printStackTrace();
    ctx.close();
  }
}
