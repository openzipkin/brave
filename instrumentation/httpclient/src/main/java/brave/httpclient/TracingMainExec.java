/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.httpclient.TracingProtocolExec.HttpRequestWrapper;
import brave.internal.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
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
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;
  @Nullable final String serverName;
  final ClientExecChain mainExec;

  TracingMainExec(HttpTracing httpTracing, ClientExecChain mainExec) {
    this.serverName = "".equals(httpTracing.serverName()) ? null : httpTracing.serverName();
    this.handler = HttpClientHandler.create(httpTracing);
    this.mainExec = mainExec;
  }

  @Override public CloseableHttpResponse execute(HttpRoute route,
    org.apache.http.client.methods.HttpRequestWrapper request,
    HttpClientContext context, HttpExecutionAware execAware)
    throws IOException, HttpException {
    Span span = (Span) context.removeAttribute(Span.class.getName());

    if (span != null) {
      handler.handleSend(new HttpRequestWrapper(request, route.getTargetHost()), span);
    }

    CloseableHttpResponse response = mainExec.execute(route, request, context, execAware);
    if (span != null) {
      if (isRemote(context, span)) {
        if (serverName != null) span.remoteServiceName(serverName);
        parseTargetAddress(route.getTargetHost(), span);
      } else {
        span.kind(null); // clear as cache hit
      }
    }
    return response;
  }

  boolean isRemote(HttpContext context, Span span) {
    return true;
  }

  static void parseTargetAddress(HttpHost target, Span span) {
    if (target == null) return;
    InetAddress address = target.getAddress();
    if (address != null) {
      if (span.remoteIpAndPort(address.getHostAddress(), target.getPort())) return;
    }
    span.remoteIpAndPort(target.getHostName(), target.getPort());
  }
}
