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
import brave.propagation.Propagation;

/**
 * Marks an interface for use in {@link HttpServerHandler#handleReceive(HttpServerRequest)}. This
 * gives a standard type to consider when parsing an incoming context.
 *
 * @since 5.7
 */
public abstract class HttpServerRequest {
  static final Propagation.Getter<HttpServerRequest, String> GETTER =
    new Propagation.Getter<HttpServerRequest, String>() {
      @Override public String get(HttpServerRequest carrier, String key) {
        return carrier.header(key);
      }

      @Override public String toString() {
        return "HttpServerRequest::header";
      }
    };

  /** Returns the underlying http request object. */
  public abstract Object unwrap();

  /**
   * The HTTP method, or verb, such as "GET" or "POST" or null if unreadable.
   *
   * <p>Conventionally associated with the key "http.method"
   *
   * @see HttpAdapter#method(Object)
   */
  @Nullable public abstract String method();

  /**
   * The absolute http path, without any query parameters or null if unreadable. Ex.
   * "/objects/abcd-ff"
   *
   * <p>Conventionally associated with the key "http.path"
   *
   * @see HttpAdapter#path(Object)
   */
  @Nullable public abstract String path();

  /**
   * The entire URL, including the scheme, host and query parameters if available or null if
   * unreadable.
   *
   * <p>Conventionally associated with the key "http.url"
   *
   * @see HttpAdapter#url(Object)
   */
  @Nullable public abstract String url();

  /**
   * Returns one value corresponding to the specified header, or null.
   *
   * @see HttpAdapter#requestHeader(Object, String)
   */
  @Nullable public abstract String header(String name);

  /**
   * Override and return true when it is possible to parse the {@link Span#remoteIpAndPort(String,
   * int) remote IP and port} from the {@link #unwrap() delegate}. Defaults to false.
   *
   * @see HttpServerAdapter#parseClientIpAndPort(Object, Span)
   */
  public boolean parseClientIpAndPort(Span span) {
    return false;
  }

  @Override public String toString() {
    return unwrap().toString();
  }
}
