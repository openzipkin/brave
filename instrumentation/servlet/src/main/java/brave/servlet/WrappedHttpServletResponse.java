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

import brave.http.HttpServerResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** @since 5.7 */
// public for others like sparkjava to use
public class WrappedHttpServletResponse extends HttpServerResponse {
  final ServletRuntime servlet = ServletRuntime.get();
  final HttpServletResponse delegate;
  final String method, httpRoute;

  public WrappedHttpServletResponse(HttpServletRequest req, HttpServletResponse resp) {
    if (req == null) throw new NullPointerException("req == null");
    if (resp == null) throw new NullPointerException("resp == null");
    this.delegate = resp;
    this.method = req.getMethod();
    this.httpRoute = (String) req.getAttribute("http.route");
  }

  @Override public String method() {
    return method;
  }

  @Override public String route() {
    return httpRoute;
  }

  @Override public HttpServletResponse unwrap() {
    return delegate;
  }

  @Override public int statusCode() {
    return servlet.status(delegate);
  }
}
