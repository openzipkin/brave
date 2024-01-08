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
package brave;

import brave.handler.SpanHandler;
import brave.handler.MutableSpan;
import brave.internal.Nullable;

/**
 * Abstract response type used for parsing.
 *
 * <h3>No extensions outside Brave</h3>
 * While this is an abstract type, it should not be subclassed outside the Brave repository. In
 * other words, subtypes are sealed within this source tree.
 *
 * @see Request
 * @since 5.10
 */
public abstract class Response {
  /** The remote {@link Span.Kind} describing the direction and type of the response. */
  public abstract Span.Kind spanKind();

  /**
   * The request that initiated this response or {@code null} if unknown.
   *
   * <p>Implementations should return the last wire-level request that caused this response or
   * error.
   */
  @Nullable public Request request() {
    return null;
  }

  /**
   * The error raised during response processing or {@code null} if there was none.
   *
   * <p>Lack of throwable, {@code null}, does not mean success. For example, in HTTP, there could
   * be a 409 status code with no corresponding Java exception.
   *
   * <h3>Handling errors</h3>
   * Handlers invoke {@link Span#error(Throwable)} prior to passing control to user-defined response
   * parsers. This allows any {@link SpanHandler} to see the raw error via {@link
   * MutableSpan#error()}, in case of export to a non-Zipkin backend such as metrics.
   *
   * <p>User-defined parsers can take any error here into consideration when deriving a {@link
   * SpanCustomizer#tag(String, String) "error" tag}. For example, if they prefer defaults, they do
   * nothing. If they have a better error tag value than what would be derived from the {@link
   * Throwable}, they can overwrite it.
   */
  @Nullable public abstract Throwable error();

  /**
   * Returns the underlying response object or {@code null} if there is none. Here are some response
   * objects: {@code org.apache.http.HttpResponse}, {@code org.apache.dubbo.rpc.Result}, {@code
   * org.apache.kafka.clients.producer.RecordMetadata}.
   *
   * <p>Note: Some implementations are composed of multiple types, such as a response and matched
   * route of the server. Moreover, an implementation may change the type returned due to
   * refactoring. Unless you control the implementation, cast carefully (ex using {@code
   * instanceof}) instead of presuming a specific type will always be returned.
   *
   * @since 5.10
   */
  @Nullable public abstract Object unwrap();

  @Override public String toString() {
    Object unwrapped = unwrap();
    // handles case where unwrap() returning this or null: don't NPE or stack overflow!
    if (unwrapped == null || unwrapped == this) return getClass().getSimpleName();
    return getClass().getSimpleName() + "{" + unwrapped + "}";
  }

  protected Response() { // no instances of this type: only subtypes
  }
}
