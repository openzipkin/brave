/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient;

import brave.Tracing;
import brave.http.HttpTracing;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;

public final class TracingHttpClientBuilder extends HttpClientBuilder {

  public static HttpClientBuilder create(Tracing tracing) {
    return new TracingHttpClientBuilder(HttpTracing.create(tracing));
  }

  public static HttpClientBuilder create(HttpTracing httpTracing) {
    return new TracingHttpClientBuilder(httpTracing);
  }

  final HttpTracing httpTracing;

  TracingHttpClientBuilder(HttpTracing httpTracing) { // intentionally hidden
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    this.httpTracing = httpTracing;
  }

  @Override protected ClientExecChain decorateProtocolExec(ClientExecChain protocolExec) {
    return new TracingProtocolExec(httpTracing, protocolExec);
  }

  @Override protected ClientExecChain decorateMainExec(ClientExecChain exec) {
    return new TracingMainExec(httpTracing, exec);
  }
}
