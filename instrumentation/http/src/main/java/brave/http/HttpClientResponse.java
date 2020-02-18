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
package brave.http;

import brave.Span;

/**
 * Marks an interface for use in {@link HttpClientHandler#handleReceive(Object, Throwable, Span)}.
 * This gives a standard type to consider when parsing an incoming context.
 *
 * @see HttpClientRequest
 * @since 5.7
 */
public abstract class HttpClientResponse extends HttpResponse {
  @Override public Span.Kind spanKind() {
    return Span.Kind.CLIENT;
  }

  @Override public Throwable error() {
    return null; // error() was added in v5.10, but this type was added in v5.7
  }

  /**
   * <h3>Why do we need an {@link HttpClientAdapter}?</h3>
   *
   * <p>We'd normally expect {@link HttpClientRequest} and {@link HttpClientResponse} to be used
   * directly, so not need an adapter. However, doing so would imply duplicating types that use
   * adapters, including {@link HttpClientParser} and {@link HttpSampler}. An adapter allows this
   * type to be used in existing parsers and samplers, avoiding code duplication.
   */
  // Intentionally hidden; Void type used to force generics to fail handling the wrong side
  static final class Adapter extends brave.http.HttpClientAdapter<Void, Object> {
    final HttpClientResponse delegate;
    final Object unwrapped;

    Adapter(HttpClientResponse delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
      this.unwrapped = delegate.unwrap();
      if (unwrapped == null) throw new NullPointerException("delegate.unwrap() == null");
    }

    // Skip request adapter methods
    @Override public final String method(Void request) {
      return null;
    }

    @Override public final String path(Void request) {
      return null;
    }

    @Override public final String url(Void request) {
      return null;
    }

    @Override public final String requestHeader(Void request, String name) {
      return null;
    }

    @Override public final long startTimestamp(Void request) {
      return 0L;
    }

    // Begin response adapter methods

    @Override public final String methodFromResponse(Object response) {
      if (response == unwrapped) return delegate.method();
      return null;
    }

    @Override public final String route(Object response) {
      if (response == unwrapped) return delegate.route();
      return null;
    }

    @Override public final Integer statusCode(Object response) {
      int result = statusCodeAsInt(response);
      return result == 0 ? null : result;
    }

    @Override public final int statusCodeAsInt(Object response) {
      if (response == unwrapped) return delegate.statusCode();
      return 0;
    }

    @Override public final long finishTimestamp(Object response) {
      if (response == unwrapped) return delegate.finishTimestamp();
      return 0L;
    }

    @Override public String toString() {
      return delegate.toString();
    }

    @Override public boolean equals(Object o) { // implemented to make testing easier
      if (o == this) return true;
      if (!(o instanceof Adapter)) return false;
      Adapter that = (Adapter) o;
      return delegate == that.delegate;
    }

    @Override public int hashCode() {
      return delegate.hashCode();
    }
  }
}
