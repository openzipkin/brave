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
import brave.propagation.Propagation.Getter;

/**
 * Marks an interface for use in {@link HttpServerHandler#handleReceive(HttpServerRequest)}. This
 * gives a standard type to consider when parsing an incoming context.
 *
 * @see HttpServerResponse
 * @since 5.7
 */
public abstract class HttpServerRequest extends HttpRequest {
  static final Getter<HttpServerRequest, String> GETTER = new Getter<HttpServerRequest, String>() {
    @Override public String get(HttpServerRequest carrier, String key) {
      return carrier.header(key);
    }

    @Override public String toString() {
      return "HttpServerRequest::header";
    }
  };

  /**
   * Override and return true when it is possible to parse the {@link Span#remoteIpAndPort(String,
   * int) remote IP and port} from the {@link #unwrap() delegate}. Defaults to false.
   *
   * @see HttpServerAdapter#parseClientIpAndPort(Object, Span)
   */
  public boolean parseClientIpAndPort(Span span) {
    return false;
  }

  /**
   * <h3>Why do we need an {@link HttpServerAdapter}?</h3>
   *
   * <p>We'd normally expect {@link HttpServerRequest} to be used directly, so not need an adapter.
   * However, parsing hasn't yet been converted to this type. Even if it was, there are public apis
   * that still accept instances of adapters. A bridge is needed until deprecated methods are
   * removed.
   */
  // Intentionally hidden; Void type used to force generics to fail handling the wrong side.
  @Deprecated static final class ToHttpAdapter extends brave.http.HttpServerAdapter<Object, Void> {
    final HttpServerRequest delegate;
    final Object unwrapped;

    ToHttpAdapter(HttpServerRequest delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
      this.unwrapped = delegate.unwrap();
      if (unwrapped == null) throw new NullPointerException("delegate.unwrap() == null");
    }

    @Override public final boolean parseClientIpAndPort(Object req, Span span) {
      if (req == unwrapped) {
        if (parseClientIpFromXForwardedFor(req, span)) return true;
        return delegate.parseClientIpAndPort(span);
      }
      return false;
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

  @Deprecated static final class FromHttpAdapter<Req> extends HttpServerRequest {
    final HttpServerAdapter<Req, ?> adapter;
    final Req request;

    FromHttpAdapter(HttpServerAdapter<Req, ?> adapter, Req request) {
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

    @Override public boolean parseClientIpAndPort(Span span) {
      return adapter.parseClientIpAndPort(request, span);
    }

    @Override public final String toString() {
      return request.toString();
    }
  }
}
