/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.netty.http;

import brave.Span;
import brave.Tracing;
import brave.http.HttpServerRequest;
import brave.http.HttpTracing;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.util.AttributeKey;

public final class NettyHttpTracing {
  static final AttributeKey<HttpServerRequest> REQUEST_ATTRIBUTE =
    AttributeKey.valueOf(HttpServerRequest.class.getName());
  static final AttributeKey<Span> SPAN_ATTRIBUTE = AttributeKey.valueOf(Span.class.getName());

  public static NettyHttpTracing create(Tracing tracing) {
    return new NettyHttpTracing(HttpTracing.create(tracing));
  }

  public static NettyHttpTracing create(HttpTracing httpTracing) {
    return new NettyHttpTracing(httpTracing);
  }

  final ChannelDuplexHandler serverHandler;

  NettyHttpTracing(HttpTracing httpTracing) { // intentionally hidden constructor
    serverHandler = new TracingHttpServerHandler(httpTracing);
  }

  /**
   * Returns a duplex handler that traces {@link io.netty.handler.codec.http.HttpRequest} messages.
   */
  public ChannelDuplexHandler serverHandler() {
    return serverHandler;
  }
}
