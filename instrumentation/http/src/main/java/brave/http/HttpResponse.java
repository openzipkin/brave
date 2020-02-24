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

import brave.Clock;
import brave.Response;
import brave.Span;
import brave.internal.Nullable;
import brave.propagation.TraceContext;

/**
 * Abstract response type used for parsing and sampling of http clients and servers.
 *
 * @see HttpClientResponse
 * @see HttpServerResponse
 * @since 5.10
 */
public abstract class HttpResponse extends Response {
  /**
   * The request that initiated this HTTP response or {@code null} if unknown.
   *
   * <p>Implementations should return the last wire-level request that caused this response or
   * error. HTTP properties like {@linkplain HttpRequest#path() path} and {@linkplain
   * HttpRequest#header(String) headers} might be different, due to redirects or authentication.
   * Some properties might not be visible until response processing, notably {@link #route()}.
   *
   * @since 5.10
   */
  @Override @Nullable public HttpRequest request() {
    return null;
  }

  /**
   * Returns the {@linkplain HttpRequest#method() HTTP method} of the request that caused this
   * response or {@code null} if unreadable.
   *
   * <p>Note: This value may be present even when {@link #request()} is {@code null}, but
   * implementations should implement {@link #request()} when possible.
   *
   * @see HttpRequest#method()
   * @since 5.10
   */
  @Nullable public String method() {
    HttpRequest request = request();
    return request != null ? request.method() : null;
  }

  /**
   * Returns the {@linkplain HttpRequest#route() HTTP route} of the request that caused this
   * response or {@code null} if unreadable.
   *
   * <p>Note: This value may be present even when {@link #request()} is {@code null}, but
   * implementations should implement {@link #request()} when possible.
   *
   * @see HttpRequest#route()
   * @since 5.10
   */
  @Nullable public String route() {
    HttpRequest request = request();
    return request != null ? request.route() : null;
  }

  /**
   * The HTTP status code or zero if unreadable.
   *
   * <p>Conventionally associated with the key "http.status_code"
   *
   * @since 5.10
   */
  public abstract int statusCode();

  /**
   * The timestamp in epoch microseconds of the end of this request or zero to take this implicitly
   * from the current clock. Defaults to zero.
   *
   * <p>This is helpful in two scenarios: late parsing and avoiding redundant timestamp overhead.
   * For example, you can asynchronously handle span completion without losing precision of the
   * actual end.
   *
   * <p>Note: Overriding has the same problems as using {@link Span#finish(long)}. For
   * example, it can result in negative duration if the clock used is allowed to correct backwards.
   * It can also result in misalignments in the trace, unless {@link brave.Tracing.Builder#clock(Clock)}
   * uses the same implementation.
   *
   * @see HttpRequest#startTimestamp()
   * @see brave.Span#finish(long)
   * @see brave.Tracing#clock(TraceContext)
   * @since 5.10
   */
  public long finishTimestamp() {
    return 0L;
  }

  HttpResponse() { // sealed type: only client and server
  }
}
