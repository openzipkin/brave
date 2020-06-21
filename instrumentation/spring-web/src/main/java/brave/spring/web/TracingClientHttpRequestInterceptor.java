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
package brave.spring.web;

import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpStatusCodeException;

public final class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
  public static ClientHttpRequestInterceptor create(Tracing tracing) {
    return create(HttpTracing.create(tracing));
  }

  public static ClientHttpRequestInterceptor create(HttpTracing httpTracing) {
    return new TracingClientHttpRequestInterceptor(httpTracing);
  }

  final CurrentTraceContext currentTraceContext;
  final HttpClientHandler<brave.http.HttpClientRequest, HttpClientResponse> handler;

  @Autowired TracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpClientHandler.create(httpTracing);
  }

  @Override public ClientHttpResponse intercept(HttpRequest req, byte[] body,
    ClientHttpRequestExecution execution) throws IOException {
    HttpRequestWrapper request = new HttpRequestWrapper(req);
    Span span = handler.handleSend(request);
    ClientHttpResponse response = null;
    Throwable error = null;
    try (Scope ws = currentTraceContext.newScope(span.context())) {
      return response = execution.execute(req, body);
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      handler.handleReceive(new ClientHttpResponseWrapper(request, response, error), span);
    }
  }

  static final class HttpRequestWrapper extends brave.http.HttpClientRequest {
    final HttpRequest delegate;

    HttpRequestWrapper(HttpRequest delegate) {
      this.delegate = delegate;
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public String method() {
      return delegate.getMethod().name();
    }

    @Override public String path() {
      String result = delegate.getURI().getPath(); // per JavaDoc, getURI() is never null
      return result != null && result.isEmpty() ? "/" : result;
    }

    @Override public String url() {
      return delegate.getURI().toString();
    }

    @Override public String header(String name) {
      Object result = delegate.getHeaders().getFirst(name);
      return result != null ? result.toString() : null;
    }

    @Override public void header(String name, String value) {
      delegate.getHeaders().set(name, value);
    }
  }

  static final class ClientHttpResponseWrapper extends HttpClientResponse {
    final HttpRequestWrapper request;
    @Nullable final ClientHttpResponse response;
    @Nullable final Throwable error;

    ClientHttpResponseWrapper(HttpRequestWrapper request, @Nullable ClientHttpResponse response,
      @Nullable Throwable error) {
      this.request = request;
      this.response = response;
      this.error = error;
    }

    @Override public Object unwrap() {
      return response;
    }

    @Override public HttpRequestWrapper request() {
      return request;
    }

    @Override public Throwable error() {
      return error;
    }

    @Override public int statusCode() {
      try {
        int result = response != null ? response.getRawStatusCode() : 0;
        if (result <= 0 && error instanceof HttpStatusCodeException) {
          result = ((HttpStatusCodeException) error).getRawStatusCode();
        }
        return result;
      } catch (Exception e) {
        return 0;
      }
    }
  }
}
