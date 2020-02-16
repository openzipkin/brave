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
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public final class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
  public static ClientHttpRequestInterceptor create(Tracing tracing) {
    return create(HttpTracing.create(tracing));
  }

  public static ClientHttpRequestInterceptor create(HttpTracing httpTracing) {
    return new TracingClientHttpRequestInterceptor(httpTracing);
  }

  final CurrentTraceContext currentTraceContext;
  final HttpClientHandler<brave.http.HttpClientRequest, brave.http.HttpClientResponse> handler;

  @Autowired TracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpClientHandler.create(httpTracing);
  }

  @Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
    ClientHttpRequestExecution execution) throws IOException {
    Span span = handler.handleSend(new HttpClientRequest(request));
    HttpClientResponse response = null;
    Throwable error = null;
    try (Scope ws = currentTraceContext.newScope(span.context())) {
      ClientHttpResponse result = execution.execute(request, body);
      response = new HttpClientResponse(result);
      return result;
    } catch (Throwable e) {
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
      return delegate.getMethod().name();
    }

    @Override public String path() {
      return delegate.getURI().getPath();
    }

    @Override public String url() {
      return delegate.getURI().toString();
    }

    @Override public String header(String name) {
      Object result = delegate.getHeaders().getFirst(name);
      return result instanceof String ? result.toString() : null;
    }

    @Override public void header(String name, String value) {
      delegate.getHeaders().set(name, value);
    }
  }

  static final class HttpClientResponse extends brave.http.HttpClientResponse {
    final ClientHttpResponse delegate;

    HttpClientResponse(ClientHttpResponse delegate) {
      this.delegate = delegate;
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public int statusCode() {
      try {
        return delegate.getRawStatusCode();
      } catch (IllegalArgumentException | IOException e) {
        return 0;
      }
    }
  }
}
