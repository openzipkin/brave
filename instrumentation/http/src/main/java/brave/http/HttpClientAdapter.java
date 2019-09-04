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

import zipkin2.Endpoint;

public abstract class HttpClientAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {
  /**
   * We'd normally expect {@link HttpClientRequest} and {@link HttpClientResponse} to be used
   * directly, so not need an adapter. However, doing so would imply duplicating types that use
   * adapters, including {@link HttpClientParser} and {@link HttpSampler}. This field allows the new
   * types to be used in existing parsers and samplers, avoiding code duplication.
   *
   * <p>This is intentionally not exposed public as {@link HttpClientHandler} holds the
   * responsibility of passing adapters to call sites that need them.
   *
   * @since 5.7
   */
  static final HttpClientAdapter<HttpClientRequest, HttpClientResponse> LEGACY =
    new HttpClientAdapter<HttpClientRequest, HttpClientResponse>() {
      @Override public String method(HttpClientRequest request) {
        return request.method();
      }

      @Override public String url(HttpClientRequest request) {
        return request.url();
      }

      @Override public String requestHeader(HttpClientRequest request, String name) {
        return request.header(name);
      }

      @Override public String path(HttpClientRequest request) {
        return request.path();
      }

      @Override public String methodFromResponse(HttpClientResponse response) {
        return response.method();
      }

      @Override public String route(HttpClientResponse response) {
        return response.route();
      }

      @Override public int statusCodeAsInt(HttpClientResponse response) {
        return response.statusCode();
      }

      @Override public Integer statusCode(HttpClientResponse response) {
        int result = response.statusCode();
        return result > 0 ? result : null;
      }

      @Override public String toString() {
        return "LegacyHttpClientAdapter{}";
      }
    };

  /**
   * Returns true if an IP representing the client was readable.
   *
   * @deprecated remote IP information should be added directly by instrumentation. This will be
   * removed in Brave v6.
   */
  @Deprecated public boolean parseServerIpAndPort(Req req, Endpoint.Builder builder) {
    return false;
  }
}
