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
package brave.servlet;

import brave.Span;
import brave.http.HttpServerAdapter;
import brave.http.HttpServerRequest;
import javax.servlet.http.HttpServletRequest;

/** @since 5.7 */
// public for others like sparkjava to use
public class WrappedHttpServletRequest extends HttpServerRequest {
  final HttpServletRequest delegate;

  public WrappedHttpServletRequest(HttpServletRequest delegate) {
    this.delegate = delegate;
  }

  @Override public HttpServletRequest unwrap() {
    return delegate;
  }

  /**
   * This sets the client IP:port to the {@linkplain HttpServletRequest#getRemoteAddr() remote
   * address} if the {@link HttpServerAdapter#parseClientIpFromXForwardedFor default parsing}
   * fails.
   */
  @Override public boolean parseClientIpAndPort(Span span) {
    return span.remoteIpAndPort(delegate.getRemoteAddr(), delegate.getRemotePort());
  }

  @Override public String method() {
    return delegate.getMethod();
  }

  @Override public String path() {
    return delegate.getRequestURI();
  }

  @Override public String url() {
    StringBuffer url = delegate.getRequestURL();
    if (delegate.getQueryString() != null && !delegate.getQueryString().isEmpty()) {
      url.append('?').append(delegate.getQueryString());
    }
    return url.toString();
  }

  @Override public String header(String name) {
    return delegate.getHeader(name);
  }
}
