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
package brave.http.features;

import brave.Span;
import brave.Tracer;
import brave.http.HttpClientHandler;
import brave.http.HttpTracing;
import brave.internal.Nullable;
import brave.propagation.TraceContext;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Example interceptor. Use the real deal brave-instrumentation-okhttp3 in real life */
final class TracingInterceptor implements Interceptor {
  final Tracer tracer;
  final HttpClientHandler<Request, Response> clientHandler;
  final TraceContext.Injector<Request.Builder> injector;

  TracingInterceptor(HttpTracing httpTracing) {
    tracer = httpTracing.tracing().tracer();
    clientHandler = HttpClientHandler.create(httpTracing, new OkHttpAdapter());
    injector = httpTracing.tracing().propagation().injector(Request.Builder::header);
  }

  @Override public Response intercept(Interceptor.Chain chain) throws IOException {
    Request.Builder builder = chain.request().newBuilder();
    Span span = clientHandler.handleSend(injector, builder, chain.request());
    Response response = null;
    Throwable error = null;
    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
      return response = chain.proceed(builder.build());
    } catch (IOException | RuntimeException | Error e) {
      error = e;
      throw e;
    } finally {
      clientHandler.handleReceive(response, error, span);
    }
  }

  static final class OkHttpAdapter extends brave.http.HttpClientAdapter<Request, Response> {
    @Override @Nullable public String method(Request request) {
      return request.method();
    }

    @Override @Nullable public String path(Request request) {
      return request.url().encodedPath();
    }

    @Override @Nullable public String url(Request request) {
      return request.url().toString();
    }

    @Override @Nullable public String requestHeader(Request request, String name) {
      return request.header(name);
    }

    @Override @Nullable public Integer statusCode(Response response) {
      int result = statusCodeAsInt(response);
      return result != 0 ? result : null;
    }

    @Override public int statusCodeAsInt(Response response) {
      return response.code();
    }
  }
}
