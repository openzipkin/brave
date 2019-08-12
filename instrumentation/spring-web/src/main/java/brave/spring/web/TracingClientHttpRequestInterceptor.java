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
package brave.spring.web;

import brave.Span;
import brave.Tracer;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public final class TracingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
  static final Setter<HttpHeaders, String> SETTER = new Setter<HttpHeaders, String>() {
    @Override public void put(HttpHeaders carrier, String key, String value) {
      carrier.set(key, value);
    }

    @Override public String toString() {
      return "HttpHeaders::set";
    }
  };

  public static ClientHttpRequestInterceptor create(Tracing tracing) {
    return create(HttpTracing.create(tracing));
  }

  public static ClientHttpRequestInterceptor create(HttpTracing httpTracing) {
    return new TracingClientHttpRequestInterceptor(httpTracing);
  }

  final Tracer tracer;
  final HttpClientHandler<HttpRequest, ClientHttpResponse> handler;
  final TraceContext.Injector<HttpHeaders> injector;

  @Autowired TracingClientHttpRequestInterceptor(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    injector = httpTracing.tracing().propagation().injector(SETTER);
  }

  @Override public ClientHttpResponse intercept(HttpRequest request, byte[] body,
    ClientHttpRequestExecution execution) throws IOException {
    Span span = handler.handleSend(injector, request.getHeaders(), request);
    ClientHttpResponse response = null;
    Throwable error = null;
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return response = execution.execute(request, body);
    } catch (IOException | RuntimeException | Error e) {
      error = e;
      throw e;
    } finally {
      handler.handleReceive(response, error, span);
    }
  }

  static final class HttpAdapter
    extends brave.http.HttpClientAdapter<HttpRequest, ClientHttpResponse> {

    @Override public String method(HttpRequest request) {
      return request.getMethod().name();
    }

    @Override public String url(HttpRequest request) {
      return request.getURI().toString();
    }

    @Override public String requestHeader(HttpRequest request, String name) {
      Object result = request.getHeaders().getFirst(name);
      return result != null ? result.toString() : "";
    }

    @Override @Nullable public Integer statusCode(ClientHttpResponse response) {
      int result = statusCodeAsInt(response);
      return result != 0 ? result : null;
    }

    @Override public int statusCodeAsInt(ClientHttpResponse response) {
      try {
        return response.getRawStatusCode();
      } catch (IllegalArgumentException | IOException e) {
        return 0;
      }
    }
  }
}
