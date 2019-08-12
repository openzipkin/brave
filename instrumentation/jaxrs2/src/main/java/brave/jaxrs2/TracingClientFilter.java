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
package brave.jaxrs2;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
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

  static final Setter<MultivaluedMap<String, Object>, String> SETTER =
    new Setter<MultivaluedMap<String, Object>, String>() {
      @Override
      public void put(MultivaluedMap<String, Object> carrier, String key, String value) {
        carrier.putSingle(key, value);
      }

      @Override public String toString() {
        return "MultivaluedMap::putSingle";
      }
    };

  final Tracer tracer;
  final HttpClientHandler<ClientRequestContext, ClientResponseContext> handler;
  final TraceContext.Injector<MultivaluedMap<String, Object>> injector;

  @Inject TracingClientFilter(HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    tracer = httpTracing.tracing().tracer();
    handler = HttpClientHandler.create(httpTracing, new HttpAdapter());
    injector = httpTracing.tracing().propagation().injector(SETTER);
  }

  @Override
  public void filter(ClientRequestContext request) {
    Span span = handler.handleSend(injector, request.getHeaders(), request);
    request.setProperty(SpanInScope.class.getName(), tracer.withSpanInScope(span));
  }

  @Override
  public void filter(ClientRequestContext request, ClientResponseContext response) {
    Span span = tracer.currentSpan();
    if (span == null) return;
    ((Tracer.SpanInScope) request.getProperty(Tracer.SpanInScope.class.getName())).close();
    handler.handleReceive(response, null, span);
  }

  static final class HttpAdapter
    extends brave.http.HttpClientAdapter<ClientRequestContext, ClientResponseContext> {

    @Override public String method(ClientRequestContext request) {
      return request.getMethod();
    }

    @Override public String path(ClientRequestContext request) {
      return request.getUri().getPath();
    }

    @Override public String url(ClientRequestContext request) {
      return request.getUri().toString();
    }

    @Override public String requestHeader(ClientRequestContext request, String name) {
      return request.getHeaderString(name);
    }

    @Override @Nullable public Integer statusCode(ClientResponseContext response) {
      int result = statusCodeAsInt(response);
      return result != 0 ? result : null;
    }

    @Override public int statusCodeAsInt(ClientResponseContext response) {
      int result = response.getStatus();
      return result != -1 ? result : 0;
    }
  }
}
