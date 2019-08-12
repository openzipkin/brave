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
package brave.httpclient;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientParser;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.protocol.HttpContext;

/**
 * Main exec is the first in the execution chain, so last to execute. This creates a concrete http
 * request, so this is where the span is started.
 */
class TracingMainExec implements ClientExecChain { // not final for subclassing
  static final Setter<HttpRequestWrapper, String> SETTER = // retrolambda no likey
    new Setter<HttpRequestWrapper, String>() {
      @Override public void put(HttpRequestWrapper carrier, String key, String value) {
        carrier.setHeader(key, value);
      }

      @Override public String toString() {
        return "HttpRequestWrapper::setHeader";
      }
    };
  static final HttpAdapter ADAPTER = new HttpAdapter();

  final Tracer tracer;
  final CurrentTraceContext currentTraceContext;
  final HttpClientParser parser;
  @Nullable final String serverName;
  final TraceContext.Injector<HttpRequestWrapper> injector;
  final ClientExecChain mainExec;

  TracingMainExec(HttpTracing httpTracing, ClientExecChain mainExec) {
    this.tracer = httpTracing.tracing().tracer();
    this.currentTraceContext = httpTracing.tracing().currentTraceContext();
    this.serverName = "".equals(httpTracing.serverName()) ? null : httpTracing.serverName();
    this.parser = httpTracing.clientParser();
    this.injector = httpTracing.tracing().propagation().injector(SETTER);
    this.mainExec = mainExec;
  }

  @Override public CloseableHttpResponse execute(HttpRoute route, HttpRequestWrapper request,
    HttpClientContext context, HttpExecutionAware execAware)
    throws IOException, HttpException {
    Span span = tracer.currentSpan();
    if (span != null) {
      injector.inject(span.context(), request);
      parseExceptRemote(request, span);
      span.start();
    }
    CloseableHttpResponse response = mainExec.execute(route, request, context, execAware);
    if (span != null && isRemote(context, span)) {
      parseRemote(request, span);
    }
    return response;
  }

  // We delay parsing of remote metadata in case the request is served from cache
  void parseExceptRemote(HttpRequestWrapper request, Span span) {
    if (!span.isNoop()) {
      CurrentTraceContext.Scope ws = currentTraceContext.maybeScope(span.context());
      try {
        parser.request(ADAPTER, request, span.customizer());
      } finally {
        ws.close();
      }
    }
  }

  void parseRemote(HttpRequestWrapper request, Span span) {
    HttpAdapter.parseTargetAddress(request, span);
    span.kind(Span.Kind.CLIENT);
    if (serverName != null) span.remoteServiceName(serverName);
  }

  boolean isRemote(HttpContext context, Span span) {
    return true;
  }
}
