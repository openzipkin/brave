/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.jakarta.servlet;

import brave.Span;
import brave.http.HttpServerRequest;
import brave.internal.Nullable;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

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

  @Override public final Object unwrap() {
    return delegate;
  }

  /**
   * This sets the client IP:port to the {@linkplain HttpServletRequest#getRemoteAddr() remote
   * address} if the {@link #parseClientIpFromXForwardedFor default parsing} fails.
   */
  @Override public boolean parseClientIpAndPort(Span span) {
    if (parseClientIpFromXForwardedFor(span)) return true;
    return span.remoteIpAndPort(delegate.getRemoteAddr(), delegate.getRemotePort());
  }

  @Override public final String method() {
    return delegate.getMethod();
  }

  @Override public String route() {
    Object maybeRoute = delegate.getAttribute("http.route");
    return maybeRoute instanceof String ? (String) maybeRoute : null;
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

  /** Looks for a valid request attribute "error" */
  @Nullable Throwable maybeError() {
    Object maybeError = delegate.getAttribute("error");
    if (maybeError instanceof Throwable) return (Throwable) maybeError;
    maybeError = delegate.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    if (maybeError instanceof Throwable) return (Throwable) maybeError;
    return null;
  }
}
