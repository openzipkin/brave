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
import brave.Tracer.SpanInScope;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import java.io.IOException;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;

/**
 * protocol exec is the last in the execution chain, so first to execute. We eagerly create a span
 * here so that user interceptors can see it.
 */
final class TracingProtocolExec implements ClientExecChain {
  final Tracer tracer;
  final HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;
  final ClientExecChain protocolExec;

  TracingProtocolExec(HttpTracing httpTracing, ClientExecChain protocolExec) {
    this.tracer = httpTracing.tracing().tracer();
    this.handler = HttpClientHandler.create(httpTracing);
    this.protocolExec = protocolExec;
  }

  @Override public CloseableHttpResponse execute(HttpRoute route, HttpRequestWrapper request,
    HttpClientContext clientContext, HttpExecutionAware execAware)
    throws IOException, HttpException {
    Span span = handler.nextSpan(new HttpClientRequest(request));
    HttpClientResponse response = null;
    Throwable error = null;
    try (SpanInScope ws = tracer.withSpanInScope(span)) {
      CloseableHttpResponse result = protocolExec.execute(route, request, clientContext, execAware);
      response = new HttpClientResponse(result);
      return result;
    } catch (IOException | HttpException | RuntimeException | Error e) {
      error = e;
      throw e;
    } finally {
      handler.handleReceive(response, error, span);
    }
  }

  static final class HttpClientRequest extends brave.http.HttpClientRequest {
    final HttpRequest delegate;

    HttpClientRequest(HttpRequest delegate) {
      this.delegate = delegate;
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public String method() {
      return delegate.getRequestLine().getMethod();
    }

    @Override public String path() {
      if (delegate instanceof HttpRequestWrapper) {
        return ((HttpRequestWrapper) delegate).getURI().getPath();
      }
      String result = delegate.getRequestLine().getUri();
      int queryIndex = result.indexOf('?');
      return queryIndex == -1 ? result : result.substring(0, queryIndex);
    }

    @Override public String url() {
      if (delegate instanceof HttpRequestWrapper) {
        HttpRequestWrapper wrapper = (HttpRequestWrapper) delegate;
        HttpHost target = wrapper.getTarget();
        if (target != null) return target.toURI() + wrapper.getURI();
      }
      return delegate.getRequestLine().getUri();
    }

    @Override public String header(String name) {
      Header result = delegate.getFirstHeader(name);
      return result != null ? result.getValue() : null;
    }

    @Override public void header(String name, String value) {
      delegate.setHeader(name, value);
    }
  }

  static final class HttpClientResponse extends brave.http.HttpClientResponse {
    final HttpResponse delegate;

    HttpClientResponse(HttpResponse delegate) {
      this.delegate = delegate;
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public int statusCode() {
      StatusLine statusLine = delegate.getStatusLine();
      return statusLine != null ? statusLine.getStatusCode() : 0;
    }
  }
}
