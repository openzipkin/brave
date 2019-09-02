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
package brave.vertx.web;

import brave.Span;
import brave.http.HttpServerAdapter;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;

final class VertxHttpServerRequest extends brave.http.HttpServerRequest {
  final HttpServerRequest delegate;

  VertxHttpServerRequest(HttpServerRequest delegate) {
    this.delegate = delegate;
  }

  @Override public HttpServerRequest unwrap() {
    return delegate;
  }

  @Override public String method() {
    return delegate.rawMethod();
  }

  @Override public String path() {
    return delegate.path();
  }

  @Override public String url() {
    return delegate.absoluteURI();
  }

  @Override public String header(String name) {
    return delegate.headers().get(name);
  }

  /**
   * This sets the client IP:port to the {@linkplain HttpServerRequest#remoteAddress() remote
   * address} if the {@link HttpServerAdapter#parseClientIpAndPort default parsing} fails.
   */
  @Override public boolean parseClientIpAndPort(Span span) {
    SocketAddress addr = delegate.remoteAddress();
    return span.remoteIpAndPort(addr.host(), addr.port());
  }
}
