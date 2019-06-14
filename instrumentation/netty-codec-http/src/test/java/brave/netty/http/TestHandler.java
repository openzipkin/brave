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

import brave.http.HttpTracing;
import brave.propagation.ExtraFieldPropagation;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import java.io.IOException;

import static brave.test.http.ITHttp.EXTRA_KEY;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.util.CharsetUtil.UTF_8;

class TestHandler extends ChannelInboundHandlerAdapter {
  final HttpTracing httpTracing;
  HttpRequest req;

  TestHandler(HttpTracing httpTracing) {
    this.httpTracing = httpTracing;
  }

  @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
    if (msg instanceof HttpRequest) {
      req = (HttpRequest) msg;
      String path = req.uri();
      int queryIndex = path.indexOf('?');
      if (queryIndex != -1) path = path.substring(0, queryIndex);
      String content = null;
      HttpResponseStatus status = OK;
      if (path.equals("/") && req.method().equals(HttpMethod.OPTIONS)) {
        content = null;
      } else if (path.equals("/foo")) {
        content = "bar";
      } else if (path.equals("/extra")) {
        content = ExtraFieldPropagation.get(EXTRA_KEY);
      } else if (path.equals("/child")) {
        httpTracing.tracing().tracer().nextSpan().name("child").start().finish();
        content = "happy";
      } else if (path.equals("/exception")) {
        throw new IOException("exception");
      } else if (path.equals("/badrequest")) {
        status = BAD_REQUEST;
      } else {
        status = NOT_FOUND;
      }

      writeResponse(ctx, status, content);
    }
  }

  @Override public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  void writeResponse(ChannelHandlerContext ctx, HttpResponseStatus responseStatus, String content) {
    if (HttpUtil.is100ContinueExpected(req)) {
      ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
    }
    boolean keepAlive = HttpUtil.isKeepAlive(req);
    FullHttpResponse response;
    if (content != null) {
      response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(content, UTF_8));
      response.headers().set(CONTENT_TYPE, "text/plain");
      response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
    } else {
      response = new DefaultFullHttpResponse(HTTP_1_1, OK);
      response.headers().set(CONTENT_LENGTH, 0);
    }
    response.setStatus(responseStatus);

    if (!keepAlive) {
      ctx.write(response).addListener(ChannelFutureListener.CLOSE);
    } else {
      response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      ctx.write(response);
    }
  }

  @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    ctx.fireExceptionCaught(cause);
    writeResponse(ctx, INTERNAL_SERVER_ERROR, "");
  }
}
