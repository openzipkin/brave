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
package brave.http.features.secondary_sampling;

import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

class TracedNode {
  // gateway -> api -> auth -> cache  -> authdb
  //                -> recommendations -> cache -> recodb
  //                -> playback -> license -> cache -> licensedb
  //                            -> moviemetadata
  //                            -> streams
  static TracedNode createServiceGraph(Function<String, Tracing> tracingFunction) {
    TracedNode.Factory nodeFactory = new TracedNode.Factory(tracingFunction);
    TracedNode gateway = nodeFactory.create("gateway");
    TracedNode api = nodeFactory.create("api", (endpoint, serviceName) -> {
      if (serviceName.equals("playback")) return endpoint.equals("/play");
      if (serviceName.equals("recommendations")) return endpoint.equals("/recommend");
      return true;
    });
    gateway.addDownStream(api);
    TracedNode auth = nodeFactory.create("auth");
    api.addDownStream(auth);
    auth.addDownStream(nodeFactory.create("cache").addDownStream(nodeFactory.create("authdb")));
    api.addDownStream(nodeFactory.create("recommendations")
      .addDownStream(nodeFactory.create("cache")
        .addDownStream(nodeFactory.create("recodb"))));
    TracedNode playback = nodeFactory.create("playback");
    api.addDownStream(playback);
    playback.addDownStream(nodeFactory.create("license")
      .addDownStream(nodeFactory.create("cache")
        .addDownStream(nodeFactory.create("licensedb"))));
    playback.addDownStream(nodeFactory.create("moviemetadata"));
    playback.addDownStream(nodeFactory.create("streams"));
    return gateway;
  }

  static class Factory {
    final Function<String, Tracing> tracingFunction;

    Factory(Function<String, Tracing> tracingFunction) {
      this.tracingFunction = tracingFunction;
    }

    TracedNode create(String serviceName) {
      return new TracedNode(serviceName, tracingFunction, (endpoint, remoteServiceName) -> true);
    }

    TracedNode create(String serviceName, BiPredicate<String, String> routeFunction) {
      return new TracedNode(serviceName, tracingFunction, routeFunction);
    }
  }

  final String localServiceName;
  final BiPredicate<String, String> routeFunction;
  final List<TracedNode> downstream = new ArrayList<>();
  final CurrentTraceContext current;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler;
  final HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler;

  TracedNode(String localServiceName, Function<String, Tracing> tracingFunction,
    BiPredicate<String, String> routeFunction) {
    this.localServiceName = localServiceName;
    this.routeFunction = routeFunction;
    Tracing tracing = tracingFunction.apply(localServiceName);
    this.current = tracing.currentTraceContext();
    HttpTracing httpTracing = HttpTracing.create(tracingFunction.apply(localServiceName));
    this.serverHandler = HttpServerHandler.create(httpTracing);
    this.clientHandler = HttpClientHandler.create(httpTracing);
  }

  TracedNode addDownStream(TracedNode downstream) {
    this.downstream.add(downstream);
    return this;
  }

  void execute(String path, Map<String, String> headers) {
    ServerRequest serverRequest = new ServerRequest(path, headers);
    Span span = serverHandler.handleReceive(serverRequest);
    try (Scope ws = current.newScope(span.context())) {
      for (TracedNode down : downstream) {
        if (routeFunction.test(serverRequest.path, down.localServiceName)) {
          callDownstream(down, serverRequest.path);
        }
      }
    }
    serverHandler.handleSend(new ServerResponse(), null, span);
  }

  void callDownstream(TracedNode down, String endpoint) {
    ClientRequest clientRequest = new ClientRequest(endpoint);
    Span span = clientHandler.handleSend(clientRequest);
    down.execute(endpoint, clientRequest.headers);
    clientHandler.handleReceive(new ClientResponse(), null, span);
  }

  static final class ServerRequest extends HttpServerRequest {
    final String path;
    final Map<String, String> headers;

    ServerRequest(String path, Map<String, String> headers) {
      this.path = path;
      this.headers = headers;
    }

    @Override public Object unwrap() {
      return this;
    }

    @Override public String method() {
      return "GET";
    }

    @Override public String path() {
      return path;
    }

    @Override public String url() {
      return null;
    }

    @Override public String header(String name) {
      return headers.get(name);
    }
  }

  static final class ServerResponse extends HttpServerResponse {
    @Override public Object unwrap() {
      return this;
    }

    @Override public int statusCode() {
      return 200;
    }
  }

  static final class ClientRequest extends HttpClientRequest {
    final String path;
    final Map<String, String> headers = new LinkedHashMap<>();

    ClientRequest(String path) {
      this.path = path;
    }

    @Override public Object unwrap() {
      return this;
    }

    @Override public String method() {
      return "GET";
    }

    @Override public String path() {
      return path;
    }

    @Override public String url() {
      return null;
    }

    @Override public String header(String name) {
      return headers.get(name);
    }

    @Override public void header(String name, String value) {
      headers.put(name, value);
    }
  }

  static final class ClientResponse extends HttpClientResponse {
    @Override public Object unwrap() {
      return this;
    }

    @Override public int statusCode() {
      return 200;
    }
  }
}
