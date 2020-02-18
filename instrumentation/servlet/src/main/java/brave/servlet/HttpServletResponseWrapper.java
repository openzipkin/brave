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

import brave.http.HttpServerResponse;
import brave.internal.Nullable;
import brave.servlet.internal.ServletRuntime;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static brave.servlet.internal.ServletRuntime.maybeError;

/**
 * This delegates to {@link HttpServletResponse} methods, taking care to portably handle {@link
 * #statusCode()}.
 *
 * @since 5.10
 */
// Public for use in sparkjava or other frameworks that re-use servlet types
public class HttpServletResponseWrapper extends HttpServerResponse { // not final for inner subtype
  /**
   * Looks for the {@link HttpServletRequest#setAttribute(String, Object) request attributes}
   * "http.route" and "error" to customize the result.
   *
   * @since 5.10
   */
  public static HttpServerResponse create(@Nullable HttpServletRequest req,
    HttpServletResponse res, @Nullable Throwable error) {
    if (req == null) return new HttpServletResponseWrapper(res, error);
    return new WithMethodAndRoute(req, res, error);
  }

  final HttpServletResponse delegate;
  @Nullable final Throwable error;

  HttpServletResponseWrapper(HttpServletResponse delegate, @Nullable Throwable error) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    this.delegate = delegate;
    this.error = error;
  }

  @Override public final int statusCode() {
    return ServletRuntime.get().status(delegate);
  }

  @Override public Throwable error() {
    return null;
  }

  @Override public final Object unwrap() {
    return delegate;
  }

  static final class WithMethodAndRoute extends HttpServletResponseWrapper {
    final String method, route;

    WithMethodAndRoute(HttpServletRequest req, HttpServletResponse res, @Nullable Throwable error) {
      super(res, maybeError(error, req));
      this.method = req.getMethod();
      Object maybeRoute = req.getAttribute("http.route");
      this.route = maybeRoute instanceof String ? (String) maybeRoute : null;
    }

    @Override public String method() {
      return method;
    }

    @Override public String route() {
      return route;
    }
  }
}
