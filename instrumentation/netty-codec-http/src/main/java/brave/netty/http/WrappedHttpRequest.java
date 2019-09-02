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

import brave.Span;
import brave.http.HttpServerRequest;
import brave.internal.Nullable;
import brave.internal.Platform;
import io.netty.handler.codec.http.HttpRequest;
import java.net.InetSocketAddress;
import java.net.URI;

final class WrappedHttpRequest extends HttpServerRequest {
  final HttpRequest request;
  @Nullable final InetSocketAddress remoteAddress;

  WrappedHttpRequest(HttpRequest request, InetSocketAddress remoteAddress) {
    this.request = request;
    this.remoteAddress = remoteAddress;
  }

  @Override public HttpRequest unwrap() {
    return request;
  }

  @Override public boolean parseClientIpAndPort(Span span) {
    if (remoteAddress.getAddress() == null) return false;
    return span.remoteIpAndPort(Platform.get().getHostString(remoteAddress),
      remoteAddress.getPort());
  }

  @Override public String method() {
    return request.method().name();
  }

  @Override public String path() {
    return URI.create(request.uri()).getPath(); // TODO benchmark
  }

  @Override public String url() {
    String host = header("Host");
    if (host == null) return null;
    // TODO: we don't know if this is really http or https!
    return "http://" + host + request.uri();
  }

  @Override public String header(String name) {
    return request.headers().get(name);
  }
}
