/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient5;

import brave.Span;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import java.io.IOException;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChain.Scope;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;

import static brave.httpclient5.HttpClientUtils.parseTargetAddress;

class HandleSendHandler implements ExecChainHandler {

  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

  HandleSendHandler(HttpTracing httpTracing) {
    this.handler = HttpClientHandler.create(httpTracing);
  }

  @Override
  public ClassicHttpResponse execute(
    ClassicHttpRequest classicHttpRequest,
    Scope scope,
    ExecChain execChain) throws IOException, HttpException {

    Span span = (Span) scope.clientContext.removeAttribute(Span.class.getName());

    if (span != null) {
      handler.handleSend(new HttpRequestWrapper(classicHttpRequest, scope.route.getTargetHost()),
        span);
    }

    ClassicHttpResponse response = execChain.proceed(classicHttpRequest, scope);
    if (span != null) {
      parseTargetAddress(scope.route.getTargetHost(), span);
    }
    return response;
  }
}
