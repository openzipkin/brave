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

/**
 * Abstract request type used for parsing and sampling of http clients and servers.
 *
 * @see HttpClientRequest
 * @see HttpServerRequest
 * @since 5.8
 */
public abstract class HttpRequest {
  /**
   * Returns the underlying http request object. Ex. {@code org.apache.http.HttpRequest}
   *
   * <p>Note: Some implementations are composed of multiple types, such as a request and a socket
   * address of the client. Moreover, an implementation may change the type returned due to
   * refactoring. Unless you control the implementation, cast carefully (ex using {@code instance
   * of}) instead of presuming a specific type will always be returned.
   */
  public abstract Object unwrap();

  /** @see HttpAdapter#startTimestamp(Object) */
  public long startTimestamp() {
    return 0L;
  }

  /** @see HttpAdapter#method(Object) */
  @Nullable public abstract String method();

  /** @see HttpAdapter#path(Object) */
  @Nullable public abstract String path();

  /** @see HttpAdapter#url(Object) */
  @Nullable public abstract String url();

  /** @see HttpAdapter#requestHeader(Object, String) */
  @Nullable public abstract String header(String name);

  @Override public String toString() {
    Object unwrapped = unwrap();
    // unwrap() returning null is a bug. It could also return this. don't NPE or stack overflow!
    if (unwrapped == null || unwrapped == this) return getClass().getSimpleName();
    return getClass().getSimpleName() + "{" + unwrapped + "}";
  }

  HttpRequest() { // sealed type: only client and server
  }
}
