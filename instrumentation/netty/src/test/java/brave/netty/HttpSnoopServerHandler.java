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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import java.io.IOException;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Values;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpSnoopServerHandler extends ChannelInboundHandlerAdapter {

  private FullHttpRequest httpRequest;
  private HttpTracing httpTracing;

  public HttpSnoopServerHandler(HttpTracing httpTracing) {
    this.httpTracing = httpTracing;
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.flush();
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof FullHttpRequest) {
      httpRequest = (FullHttpRequest) msg;
    }
    String uri = httpRequest.uri();
    String content = null;

    if (uri.startsWith("/foo")) {
      content = "bar";
    } else if (uri.startsWith("/child")) {
      httpTracing.tracing().tracer().nextSpan().name("child").start().finish();
      content = "happy";
    } else if (uri.startsWith("/disconnect")) {
      throw new IOException();
    } else {//not found
      writeResponse(NOT_FOUND, null, ctx);
    }

    writeResponse(content, ctx);
  }

  private void writeResponse(String content,
      ChannelHandlerContext ctx) {
    writeResponse(null, content, ctx);
  }

  private boolean writeResponse(HttpResponseStatus responseStatus, String content,
      ChannelHandlerContext ctx) {
    if (StringUtil.isNullOrEmpty(content)) {
      content = Unpooled.EMPTY_BUFFER.toString();
    }
    // Decide whether to close the connection or not.
    boolean keepAlive = isKeepAlive(httpRequest);

    if (responseStatus == null) {
      responseStatus = httpRequest.getDecoderResult().isSuccess() ? OK : BAD_REQUEST;
    }
    // Build the response object.
    FullHttpResponse response = new DefaultFullHttpResponse(
        HTTP_1_1, responseStatus,
        Unpooled.copiedBuffer(content, CharsetUtil.UTF_8));

    response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

    if (keepAlive) {
      // Add 'Content-Length' header only for a keep-alive connection.
      response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
      // Add keep alive header as per:
      // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
      response.headers().set(CONNECTION, Values.KEEP_ALIVE);
    }

    // Write the response.
    ctx.write(response);

    return keepAlive;
  }


   /* @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)throws Exception{
        //ctx.fireExceptionCaught(cause);
    }*/
}
