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
 * <h3>Why does this extend {@link HttpClientAdapter}?</h3>
 *
 * <p>We'd normally expect {@link HttpClientRequest} and {@link HttpClientResponse} to be used
 * directly, so not need an adapter. However, doing so would imply duplicating types that use
 * adapters, including {@link HttpClientParser} and {@link HttpSampler}. Retrofiting this as an
 * adapter allows this type to be used in existing parsers and samplers, avoiding code duplication.
 *
 * @see HttpClientResponse
 * @since 5.7
 */
// Void type used to force generics to fail handling the wrong side
public abstract class HttpClientRequest extends HttpClientAdapter<Object, Void> {
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
    return unwrap().toString();
  }

  // Begin request adapter methods

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String method(Object request) {
    if (request == unwrap()) return method();
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String url(Object request) {
    if (request == unwrap()) return url();
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String requestHeader(Object request, String name) {
    if (request == unwrap()) return header(name);
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String path(Object request) {
    if (request == unwrap()) return path();
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final long startTimestamp(Object request) {
    if (request == unwrap()) return startTimestamp();
    return 0L;
  }

  // Skip response adapter methods

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String methodFromResponse(Void response) {
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String route(Void response) {
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final int statusCodeAsInt(Void response) {
    return 0;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final Integer statusCode(Void response) {
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final long finishTimestamp(Void response) {
    return 0L;
  }
}
