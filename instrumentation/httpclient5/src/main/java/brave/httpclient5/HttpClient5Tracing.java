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

package brave.httpclient5;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import static org.apache.hc.client5.http.impl.ChainElement.PROTOCOL;

public class HttpClient5Tracing {

  final HttpTracing httpTracing;

  HttpClient5Tracing(HttpTracing httpTracing) {
    this.httpTracing = httpTracing;
  }

  public static HttpClient5Tracing newBuilder(Tracing tracing) {
    return new HttpClient5Tracing(HttpTracing.create(tracing));
  }

  public static HttpClient5Tracing newBuilder(HttpTracing httpTracing) {
    return new HttpClient5Tracing(httpTracing);
  }

  public CloseableHttpClient create(HttpClientBuilder httpClientBuilder) {
    httpClientBuilder.addExecInterceptorBefore(ChainElement.MAIN_TRANSPORT.name(),
      HandleSendHandler.class.getName(),
      new HandleSendHandler(httpTracing));
    httpClientBuilder.addExecInterceptorBefore(ChainElement.PROTOCOL.name(),
      HandleReceiveHandler.class.getName(),
      new HandleReceiveHandler(httpTracing));
    return httpClientBuilder.build();
  }

  public CloseableHttpAsyncClient create(HttpAsyncClientBuilder httpAsyncClientBuilder) {
    final CurrentTraceContext currentTraceContext = httpTracing.tracing().currentTraceContext();
    httpAsyncClientBuilder.addExecInterceptorBefore(PROTOCOL.name(),
      AsyncHandleSendHandler.class.getName(),
      new AsyncHandleSendHandler(httpTracing));
    httpAsyncClientBuilder.addRequestInterceptorFirst(
      (httpRequest, entityDetails, httpContext) -> HttpClientUtils.openScope(httpContext,
        currentTraceContext));
    httpAsyncClientBuilder.addRequestInterceptorLast(
      (httpRequest, entityDetails, httpContext) -> HttpClientUtils.closeScope(httpContext));
    httpAsyncClientBuilder.addResponseInterceptorFirst(
      (httpResponse, entityDetails, httpContext) -> HttpClientUtils.openScope(httpContext,
        currentTraceContext));
    httpAsyncClientBuilder.addResponseInterceptorLast(
      (httpResponse, entityDetails, httpContext) -> HttpClientUtils.closeScope(httpContext));
    return new TracingHttpAsyncClient(httpAsyncClientBuilder.build(), currentTraceContext);
  }

  public CloseableHttpAsyncClient create(H2AsyncClientBuilder h2AsyncClientBuilder) {
    final CurrentTraceContext currentTraceContext = httpTracing.tracing().currentTraceContext();
    h2AsyncClientBuilder.addExecInterceptorBefore(PROTOCOL.name(),
      AsyncHandleSendHandler.class.getName(),
      new AsyncHandleSendHandler(httpTracing));
    h2AsyncClientBuilder.addRequestInterceptorFirst(
      (httpRequest, entityDetails, httpContext) -> HttpClientUtils.openScope(httpContext,
        currentTraceContext));
    h2AsyncClientBuilder.addRequestInterceptorLast(
      (httpRequest, entityDetails, httpContext) -> HttpClientUtils.closeScope(httpContext));
    h2AsyncClientBuilder.addResponseInterceptorFirst(
      (httpResponse, entityDetails, httpContext) -> HttpClientUtils.openScope(httpContext,
        currentTraceContext));
    h2AsyncClientBuilder.addResponseInterceptorLast(
      (httpResponse, entityDetails, httpContext) -> HttpClientUtils.closeScope(httpContext));
    return new TracingHttpAsyncClient(h2AsyncClientBuilder.build(), currentTraceContext);
  }
}
