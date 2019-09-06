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

/**
 * Marks an interface for use in {@link HttpClientHandler#handleReceive(Object, Throwable, Span)}.
 * This gives a standard type to consider when parsing an incoming context.
 *
 * @see HttpClientRequest
 * @since 5.7
 */
public abstract class HttpClientResponse {
  /**
   * Returns the underlying http response object. Ex. {@code org.apache.http.HttpResponse}
   *
   * <p>Note: Some implementations are composed of multiple types, such as a response and an object
   * representing the matched route. Moreover, an implementation may change the type returned due to
   * refactoring. Unless you control the implementation, cast carefully (ex using {@code instance
   * of}) instead of presuming a specific type will always be returned.
   */
  public abstract Object unwrap();

  /** @see HttpAdapter#methodFromResponse(Object) */
  @Nullable public String method() {
    return null;
  }

  /** @see HttpAdapter#route(Object) */
  @Nullable public String route() {
    return null;
  }

  /** @see HttpAdapter#statusCodeAsInt(Object) */
  public abstract int statusCode();

  /** @see HttpAdapter#finishTimestamp(Object) */
  public long finishTimestamp() {
    return 0L;
  }

  @Override public String toString() {
    // unwrap() returning null is a bug, but don't NPE during toString()
    return "HttpServerResponse{" + unwrap() + "}";
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
