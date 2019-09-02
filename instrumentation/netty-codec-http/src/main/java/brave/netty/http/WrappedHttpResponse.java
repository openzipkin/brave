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
package brave.netty.http;

import brave.http.HttpServerResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

final class WrappedHttpResponse extends HttpServerResponse {
  final HttpResponse delegate;

  WrappedHttpResponse(HttpResponse delegate) {
    this.delegate = delegate;
  }

  @Override public HttpResponse unwrap() {
    return delegate;
  }

  @Override public int statusCode() {
    HttpResponseStatus status = delegate.status();
    return status != null ? status.code() : 0;
  }
}
