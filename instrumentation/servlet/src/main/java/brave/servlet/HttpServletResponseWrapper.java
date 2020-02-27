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
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This delegates to {@link HttpServletResponse} methods, taking care to portably handle {@link
 * #statusCode()}.
 *
 * @since 5.10
 */
// Public for use in sparkjava or other frameworks that re-use servlet types
public class HttpServletResponseWrapper extends HttpServerResponse { // not final for inner subtype
  /**
   * @param caught an exception caught serving the request.
   * @since 5.10
   */
  public static HttpServerResponse create(@Nullable HttpServletRequest request,
    HttpServletResponse response, @Nullable Throwable caught) {
    return new HttpServletResponseWrapper(request, response, caught);
  }

  @Nullable final HttpServletRequestWrapper request;
  final HttpServletResponse response;
  @Nullable final Throwable caught;

  HttpServletResponseWrapper(@Nullable HttpServletRequest request, HttpServletResponse response,
    @Nullable Throwable caught) {
    if (response == null) throw new NullPointerException("response == null");
    this.request = request != null ? new HttpServletRequestWrapper(request) : null;
    this.response = response;
    this.caught = caught;
  }

  @Override public final Object unwrap() {
    return response;
  }

  @Override @Nullable public HttpServletRequestWrapper request() {
    return request;
  }

  @Override public Throwable error() {
    if (caught != null) return caught;
    if (request == null) return null;
    return request.maybeError();
  }

  @Override public int statusCode() {
    int result = ServletRuntime.get().status(response);
    if (caught != null && result == 200) { // We may have a potentially bad status due to defaults
      // Servlet only seems to define one exception that has a built-in code. Logic in Jetty
      // defaults the status to 500 otherwise.
      if (caught instanceof UnavailableException) {
        return ((UnavailableException) caught).isPermanent() ? 404 : 503;
      }
      return 500;
    }
    return result;
  }
}
