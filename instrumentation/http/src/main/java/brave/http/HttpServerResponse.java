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
import brave.internal.Nullable;

/**
 * Marks an interface for use in {@link HttpServerHandler#handleSend(Object, Throwable, Span)}. This
 * gives a standard type to consider when parsing an outgoing context.
 *
 * @see HttpServerRequest
 * @since 5.7
 */
public abstract class HttpServerResponse extends HttpResponse {
  @Override public final Span.Kind spanKind() {
    return Span.Kind.SERVER;
  }

  /** {@inheritDoc} */
  @Override @Nullable public HttpServerRequest request() {
    return null;
  }

  @Override public Throwable error() {
    return null; // error() was added in v5.10, but this type was added in v5.7
  }
}
