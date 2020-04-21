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
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;

/**
 * Marks an interface for use in {@link HttpClientHandler#handleSend(HttpClientRequest)}. This gives
 * a standard type to consider when parsing an outgoing context.
 *
 * @see HttpClientResponse
 * @since 5.7
 */
public abstract class HttpClientRequest extends HttpRequest {
  static final Setter<HttpClientRequest, String> SETTER = new Setter<HttpClientRequest, String>() {
    @Override public void put(HttpClientRequest request, String key, String value) {
      request.header(key, value);
    }

    @Override public String toString() {
      return "HttpClientRequest::header";
    }
  };

  @Override public final Span.Kind spanKind() {
    return Span.Kind.CLIENT;
  }

  /**
   * Sets a request header with the indicated name. {@code null} values are unsupported.
   *
   * <p>This is only used when {@link TraceContext.Injector#inject(TraceContext, Object) injecting}
   * a trace context as internally implemented by {link HttpClientHandler}. Calls during sampling or
   * parsing are invalid and may be ignored by instrumentation.
   *
   * @see #SETTER
   * @since 5.7
   */
  public abstract void header(String name, String value);
}
