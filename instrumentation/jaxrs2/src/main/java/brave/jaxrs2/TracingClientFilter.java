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
package brave.jaxrs2;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.ext.Provider;

import static javax.ws.rs.RuntimeType.CLIENT;

/**
 * This filter is set at highest priority which means it executes before other filters. The impact
 * is other filters can modify the span created here via {@link Tracer#currentSpanCustomizer()}.
 * Another impact is the span will not see modifications to the request made by downstream filters.
 */
// If tags for the request are added on response, they might include changes made by other filters..
// However, the response callback isn't invoked on error, so this choice could be worse.
@Provider
@ConstrainedTo(CLIENT)
@Priority(0) // to make the span in scope visible to other filters
// Public to allow this to be used in conjunction with jersey-server instrumentation, without also
// registering TracingContainerFilter (which would lead to redundant spans).
public final class TracingClientFilter implements ClientRequestFilter, ClientResponseFilter {
  public static TracingClientFilter create(Tracing tracing) {
    return new TracingClientFilter(HttpTracing.create(tracing));
  }

  public static TracingClientFilter create(HttpTracing httpTracing) {
    return new TracingClientFilter(httpTracing);
  }

  final Tracer tracer;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

  @Inject TracingClientFilter(HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    tracer = httpTracing.tracing().tracer();
    handler = HttpClientHandler.create(httpTracing);
  }

  @Override public void filter(ClientRequestContext request) {
    Span span = handler.handleSend(new ClientRequestContextWrapper(request));
    request.setProperty(SpanInScope.class.getName(), tracer.withSpanInScope(span));
  }

  @Override public void filter(ClientRequestContext request, ClientResponseContext response) {
    Span span = tracer.currentSpan();
    if (span == null) return;
    ((SpanInScope) request.getProperty(SpanInScope.class.getName())).close();
    handler.handleReceive(new ClientResponseContextWrapper(request, response), span);
  }

  static final class ClientRequestContextWrapper extends HttpClientRequest {
    final ClientRequestContext delegate;

    ClientRequestContextWrapper(ClientRequestContext delegate) {
      this.delegate = delegate;
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public String method() {
      return delegate.getMethod();
    }

    @Override public String path() {
      String result = delegate.getUri().getPath();
      return result != null && result.isEmpty() ? "/" : result;
    }

    @Override public String url() {
      return delegate.getUri().toString();
    }

    @Override public String header(String name) {
      return delegate.getHeaderString(name);
    }

    @Override public void header(String name, String value) {
      delegate.getHeaders().putSingle(name, value);
    }
  }

  static final class ClientResponseContextWrapper extends HttpClientResponse {
    final ClientRequestContextWrapper request;
    final ClientResponseContext response;

    ClientResponseContextWrapper(
      ClientRequestContext request, ClientResponseContext response) {
      this.request = new ClientRequestContextWrapper(request);
      this.response = response;
    }

    @Override public Object unwrap() {
      return response;
    }

    @Override public ClientRequestContextWrapper request() {
      return request;
    }

    @Override public Throwable error() {
      return null; // jax-rs has no visibility into errors
    }

    @Override public int statusCode() {
      int result = response.getStatus();
      return result != -1 ? result : 0;
    }
  }
}
