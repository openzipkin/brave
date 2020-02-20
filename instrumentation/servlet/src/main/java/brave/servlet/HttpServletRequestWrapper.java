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
package brave.servlet;

import brave.Span;
import brave.http.HttpServerRequest;
import javax.servlet.http.HttpServletRequest;

/**
 * Besides delegating to {@link HttpServletRequest} methods, this also parses the remote IP of the
 * client.
 *
 * @since 5.10
 */
// Public for use in sparkjava or other frameworks that re-use servlet types
public final class HttpServletRequestWrapper extends HttpServerRequest {
  /** @since 5.10 */
  public static HttpServerRequest create(HttpServletRequest request) {
    return new HttpServletRequestWrapper(request);
  }

  final HttpServletRequest delegate;

  HttpServletRequestWrapper(HttpServletRequest delegate) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
  }

  /**
   * This sets the client IP:port to the {@linkplain HttpServletRequest#getRemoteAddr() remote
   * address} if the {@link #parseClientIpAndPort default parsing} fails.
   */
  @Override public boolean parseClientIpAndPort(Span span) {
    if (parseClientIpFromXForwardedFor(span)) return true;
    return span.remoteIpAndPort(delegate.getRemoteAddr(), delegate.getRemotePort());
  }

  @Override public final String method() {
    return delegate.getMethod();
  }

  @Override public final String path() {
    return delegate.getRequestURI();
  }

  // not final as some implementations may be able to do this more efficiently
  @Override public String url() {
    StringBuffer url = delegate.getRequestURL();
    if (delegate.getQueryString() != null && !delegate.getQueryString().isEmpty()) {
      url.append('?').append(delegate.getQueryString());
    }
    return url.toString();
  }

  @Override public final String header(String name) {
    return delegate.getHeader(name);
  }

  @Override public final Object unwrap() {
    return delegate;
  }
}
