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
package brave.httpclient;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.httpclient.TracingProtocolExec.HttpRequestWrapper;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.protocol.HttpContext;

/**
 * Main exec is the first in the execution chain, so last to execute. This creates a concrete http
 * request, so this is where the span is started.
 */
class TracingMainExec implements ClientExecChain { // not final for subclassing
  final Tracer tracer;
  final CurrentTraceContext currentTraceContext;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;
  @Nullable final String serverName;
  final ClientExecChain mainExec;

  TracingMainExec(HttpTracing httpTracing, ClientExecChain mainExec) {
    this.tracer = httpTracing.tracing().tracer();
    this.currentTraceContext = httpTracing.tracing().currentTraceContext();
    this.serverName = "".equals(httpTracing.serverName()) ? null : httpTracing.serverName();
    this.handler = HttpClientHandler.create(httpTracing);
    this.mainExec = mainExec;
  }

  @Override public CloseableHttpResponse execute(HttpRoute route,
    org.apache.http.client.methods.HttpRequestWrapper request,
    HttpClientContext context, HttpExecutionAware execAware)
    throws IOException, HttpException {
    Span span = tracer.currentSpan();
    if (span != null) handler.handleSend(new HttpRequestWrapper(request), span);

    CloseableHttpResponse response = mainExec.execute(route, request, context, execAware);
    if (span != null) {
      if (isRemote(context, span)) {
        if (serverName != null) span.remoteServiceName(serverName);
        HttpAdapter.parseTargetAddress(request, span);
      } else {
        span.kind(null); // clear as cache hit
      }
    }
    return response;
  }

  boolean isRemote(HttpContext context, Span span) {
    return true;
  }
}
