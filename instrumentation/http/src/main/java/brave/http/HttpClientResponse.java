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
// Void type used to force generics to fail handling the wrong side
public abstract class HttpClientResponse extends HttpClientAdapter<Void, Object> {
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
    return unwrap().toString();
  }

  // Skip request adapter methods

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String method(Void request) {
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String path(Void request) {
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String url(Void request) {
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String requestHeader(Void request, String name) {
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final long startTimestamp(Void request) {
    return 0L;
  }

  // Begin response adapter methods

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String methodFromResponse(Object response) {
    if (response == unwrap()) return method();
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final String route(Object response) {
    if (response == unwrap()) return route();
    return null;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final Integer statusCode(Object response) {
    int result = statusCodeAsInt(response);
    return result == 0 ? null : result;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final int statusCodeAsInt(Object response) {
    if (response == unwrap()) return statusCode();
    return 0;
  }

  /** @deprecated this only exists to bridge this type to existing samplers and parsers. */
  @Deprecated @Override public final long finishTimestamp(Object response) {
    if (response == unwrap()) return finishTimestamp();
    return 0L;
  }
}
