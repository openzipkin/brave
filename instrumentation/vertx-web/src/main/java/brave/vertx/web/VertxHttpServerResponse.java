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

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

final class VertxHttpServerResponse extends brave.http.HttpServerResponse {
  final HttpServerResponse delegate;
  final String method, httpRoute;

  VertxHttpServerResponse(RoutingContext context) {
    this.delegate = context.response();
    this.method = context.request().rawMethod();
    String httpRoute = context.currentRoute().getPath();
    this.httpRoute = httpRoute != null ? httpRoute : "";
  }

  @Override public HttpServerResponse unwrap() {
    return delegate;
  }

  @Override public int statusCode() {
    return delegate.getStatusCode();
  }

  @Override public String method() {
    return method;
  }

  @Override public String route() {
    return httpRoute;
  }
}
