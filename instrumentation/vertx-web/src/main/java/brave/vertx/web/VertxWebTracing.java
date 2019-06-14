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

import brave.Tracing;
import brave.http.HttpTracing;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public final class VertxWebTracing {
  public static VertxWebTracing create(Tracing tracing) {
    return new VertxWebTracing(HttpTracing.create(tracing));
  }

  public static VertxWebTracing create(HttpTracing httpTracing) {
    return new VertxWebTracing(httpTracing);
  }

  final HttpTracing httpTracing;

  VertxWebTracing(HttpTracing httpTracing) { // intentionally hidden constructor
    if (httpTracing == null) throw new NullPointerException("httpTracing == null");
    this.httpTracing = httpTracing;
  }

  /**
   * Returns a routing context handler that traces {@link HttpServerRequest} messages.
   *
   * <p>Ensure you install this both as a handler and an failure handler, ordered before routes.
   * <pre>{@code
   * routingContextHandler = vertxWebTracing.routingContextHandler();
   * router.route()
   *       .order(-1) // applies before routes
   *       .handler(routingContextHandler)
   *       .failureHandler(routingContextHandler);
   * }</pre>
   */
  public Handler<RoutingContext> routingContextHandler() {
    return new TracingRoutingContextHandler(httpTracing);
  }
}
