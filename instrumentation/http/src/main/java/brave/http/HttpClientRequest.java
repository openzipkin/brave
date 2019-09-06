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

import brave.internal.Nullable;
import brave.propagation.Propagation.Setter;

/**
 * Marks an interface for use in {@link HttpClientHandler#handleSend(HttpClientRequest)}. This gives
 * a standard type to consider when parsing an outgoing context.
 *
 * @see HttpClientResponse
 * @since 5.7
 */
public abstract class HttpClientRequest {
  static final Setter<HttpClientRequest, String> SETTER = new Setter<HttpClientRequest, String>() {
    @Override public void put(HttpClientRequest carrier, String key, String value) {
      carrier.header(key, value);
    }

    @Override public String toString() {
      return "HttpClientRequest::header";
    }
  };

  /**
   * Returns the underlying http request object. Ex. {@code org.apache.http.HttpRequest}
   *
   * <p>Note: Some implementations are composed of multiple types, such as a request and a socket
   * address of the client. Moreover, an implementation may change the type returned due to
   * refactoring. Unless you control the implementation, cast carefully (ex using {@code instance
   * of}) instead of presuming a specific type will always be returned.
   */
  public abstract Object unwrap();

  /** @see HttpAdapter#method(Object) */
  @Nullable public abstract String method();

  /** @see HttpAdapter#path(Object) */
  @Nullable public abstract String path();

  /** @see HttpAdapter#url(Object) */
  @Nullable public abstract String url();

  /** @see HttpAdapter#requestHeader(Object, String) */
  @Nullable public abstract String header(String name);

  /** @see HttpAdapter#startTimestamp(Object) */
  public long startTimestamp() {
    return 0L;
  }

  /** @see #SETTER */
  @Nullable public abstract void header(String name, String value);

  @Override public String toString() {
    // unwrap() returning null is a bug, but don't NPE during toString()
    return "HttpClientRequest{" + unwrap() + "}";
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
  static final class Adapter extends brave.http.HttpClientAdapter<Object, Void> {
    final HttpClientRequest delegate;
    final Object unwrapped;

    Adapter(HttpClientRequest delegate) {
      if (delegate == null) throw new NullPointerException("delegate == null");
      this.delegate = delegate;
      this.unwrapped = delegate.unwrap();
      if (unwrapped == null) throw new NullPointerException("delegate.unwrap() == null");
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

    @Override public final long startTimestamp(Object request) {
      if (request == unwrapped) return delegate.startTimestamp();
      return 0L;
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
