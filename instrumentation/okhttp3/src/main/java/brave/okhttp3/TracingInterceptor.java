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
package brave.okhttp3;

import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.TraceContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import static brave.okhttp3.TracingCallFactory.NULL_SENTINEL;

/**
 * This is a network-level interceptor, which creates a new span for each attempt. Note that this
 * does not work well for high traffic servers, as the span context can be lost when under backlog.
 * In cases like that, use {@link TracingCallFactory}.
 */
public final class TracingInterceptor implements Interceptor {
  public static Interceptor create(Tracing tracing) {
    return create(HttpTracing.create(tracing));
  }

  public static Interceptor create(HttpTracing httpTracing) {
    return new TracingInterceptor(httpTracing);
  }

  final CurrentTraceContext currentTraceContext;
  final HttpClientHandler<HttpClientRequest, HttpClientResponse> handler;

  TracingInterceptor(HttpTracing httpTracing) {
    if (httpTracing == null) throw new NullPointerException("HttpTracing == null");
    currentTraceContext = httpTracing.tracing().currentTraceContext();
    handler = HttpClientHandler.create(httpTracing);
  }

  @Override public Response intercept(Chain chain) throws IOException {
    RequestWrapper request = new RequestWrapper(chain.request());

    Span span;
    TraceContext parent = chain.request().tag(TraceContext.class);
    if (parent != null) { // TracingCallFactory setup this request
      span = handler.handleSendWithParent(request, parent != NULL_SENTINEL ? parent : null);
    } else { // This is using interceptors only
      span = handler.handleSend(request);
    }

    parseRouteAddress(chain, span);

    Response result = null;
    Throwable error = null;
    try (Scope ws = currentTraceContext.newScope(span.context())) {
      return result = chain.proceed(request.build());
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      ResponseWrapper response = result != null ? new ResponseWrapper(result, error) : null;
      handler.handleReceive(response, error, span);
    }
  }

  static void parseRouteAddress(Chain chain, Span span) {
    if (span.isNoop()) return;
    Connection connection = chain.connection();
    if (connection == null) return;
    InetSocketAddress socketAddress = connection.route().socketAddress();
    span.remoteIpAndPort(socketAddress.getHostString(), socketAddress.getPort());
  }

  static final class RequestWrapper extends HttpClientRequest {
    final Request delegate;
    Request.Builder builder;

    RequestWrapper(Request delegate) {
      this.delegate = delegate;
    }

    @Override public Object unwrap() {
      return delegate;
    }

    @Override public String method() {
      return delegate.method();
    }

    @Override public String path() {
      return delegate.url().encodedPath();
    }

    @Override public String url() {
      return delegate.url().toString();
    }

    @Override public String header(String name) {
      return delegate.header(name);
    }

    @Override public void header(String name, String value) {
      if (builder == null) builder = delegate.newBuilder();
      builder.header(name, value);
    }

    Request build() {
      return builder != null ? builder.build() : delegate;
    }
  }

  static final class ResponseWrapper extends HttpClientResponse {
    final RequestWrapper request;
    final Response response;
    @Nullable final Throwable error;

    ResponseWrapper(Response response, @Nullable Throwable error) {
      // intentionally not the same instance as chain.proceed, as properties may have changed
      this.request = new RequestWrapper(response.request());
      this.response = response;
      this.error = error;
    }

    @Override public Object unwrap() {
      return response;
    }

    @Override public RequestWrapper request() {
      return request;
    }

    @Override public Throwable error() {
      return error;
    }

    @Override public int statusCode() {
      return response.code();
    }
  }
}
