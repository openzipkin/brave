/*
 * Copyright 2013-2020 The OpenZipkin Authors
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
