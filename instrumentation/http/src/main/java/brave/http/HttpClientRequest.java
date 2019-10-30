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
package brave.http;

import brave.Span;
import brave.internal.Nullable;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;

/**
 * Marks an interface for use in {@link HttpClientHandler#handleSend(HttpClientRequest)}. This gives
 * a standard type to consider when parsing an outgoing context.
 *
 * @see HttpClientResponse
 * @since 5.7
 */
public abstract class HttpClientRequest extends HttpRequest {
  static final Setter<HttpClientRequest, String> SETTER = new Setter<HttpClientRequest, String>() {
    @Override public void put(HttpClientRequest carrier, String key, String value) {
      carrier.header(key, value);
    }

    @Override public String toString() {
      return "HttpClientRequest::header";
    }
  };

  @Override public final Span.Kind spanKind() {
    return Span.Kind.CLIENT;
  }

  /**
   * Sets a request header with the indicated name. Null values are unsupported.
   *
   * This is only used when {@link TraceContext.Injector#inject(TraceContext, Object) injecting} a
   * trace context as internally implemented by {link HttpClientHandler}. Calls during sampling or
   * parsing are invalid.
   *
   * @see #SETTER
   * @since 5.7
   */
  @Nullable public abstract void header(String name, String value);

  /**
   * <h3>Why do we need an {@link HttpClientAdapter}?</h3>
   *
   * <p>We'd normally expect {@link HttpClientRequest} to be used directly, so not need an adapter.
   * However, parsing hasn't yet been converted to this type. Even if it was, there are public apis
   * that still accept instances of adapters. A bridge is needed until deprecated methods are
   * removed.
   */
  // Intentionally hidden; Void type used to force generics to fail handling the wrong side.
  @Deprecated static final class ToHttpAdapter extends brave.http.HttpClientAdapter<Object, Void> {
    final HttpClientRequest delegate;
    final Object unwrapped;

    ToHttpAdapter(HttpClientRequest delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
      this.unwrapped = delegate.unwrap();
      if (unwrapped == null) throw new NullPointerException("delegate.unwrap() == null");
    }

    @Override public final long startTimestamp(Object request) {
      if (request == unwrapped) return delegate.startTimestamp();
      return 0L;
    }

    @Override public final String method(Object request) {
      if (request == unwrapped) return delegate.method();
      return null;
    }

    @Override public final String url(Object request) {
      if (request == unwrapped) return delegate.url();
      return null;
    }

    @Override public final String requestHeader(Object request, String name) {
      if (request == unwrapped) return delegate.header(name);
      return null;
    }

    @Override public final String path(Object request) {
      if (request == unwrapped) return delegate.path();
      return null;
    }

    // Skip response adapter methods

    @Override public final String methodFromResponse(Void response) {
      return null;
    }

    @Override public final String route(Void response) {
      return null;
    }

    @Override public final int statusCodeAsInt(Void response) {
      return 0;
    }

    @Override public final Integer statusCode(Void response) {
      return null;
    }

    @Override public final long finishTimestamp(Void response) {
      return 0L;
    }

    @Override public final String toString() {
      return delegate.toString();
    }
  }

  @Deprecated static final class FromHttpAdapter<Req> extends HttpClientRequest {
    final HttpClientAdapter<Req, ?> adapter;
    final Req request;

    FromHttpAdapter(HttpClientAdapter<Req, ?> adapter, Req request) {
      if (adapter == null) throw new NullPointerException("adapter == null");
      this.adapter = adapter;
      if (request == null) throw new NullPointerException("request == null");
      this.request = request;
    }

    @Override public Object unwrap() {
      return request;
    }

    @Override public long startTimestamp() {
      return adapter.startTimestamp(request);
    }

    @Override public String method() {
      return adapter.method(request);
    }

    @Override public String path() {
      return adapter.path(request);
    }

    @Override public String url() {
      return adapter.url(request);
    }

    @Override public String header(String name) {
      return adapter.requestHeader(request, name);
    }

    @Override public void header(String name, String value) {
      throw new UnsupportedOperationException("immutable");
    }

    @Override public final String toString() {
      return request.toString();
    }
  }
}
