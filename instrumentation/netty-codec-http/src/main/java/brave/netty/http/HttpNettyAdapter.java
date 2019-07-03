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

import brave.http.HttpServerAdapter;
import brave.internal.Nullable;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

final class HttpNettyAdapter extends HttpServerAdapter<HttpRequest, HttpResponse> {
  @Override public String method(HttpRequest request) {
    return request.method().name();
  }

  @Override public String url(HttpRequest request) {
    String host = requestHeader(request, "Host");
    if (host == null) return null;
    // TODO: we don't know if this is really http or https!
    return "http://" + host + request.uri();
  }

  @Override public String requestHeader(HttpRequest request, String name) {
    return request.headers().get(name);
  }

  @Override @Nullable public Integer statusCode(HttpResponse response) {
    int result = statusCodeAsInt(response);
    return result != 0 ? result : null;
  }

  @Override public int statusCodeAsInt(HttpResponse response) {
    HttpResponseStatus status = response.status();
    return status != null ? status.code() : 0;
  }
}
