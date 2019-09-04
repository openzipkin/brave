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

public abstract class HttpServerAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {
  /**
   * We'd normally expect {@link HttpServerRequest} and {@link HttpServerResponse} to be used
   * directly, so not need an adapter. However, doing so would imply duplicating types that use
   * adapters, including {@link HttpServerParser} and {@link HttpSampler}. This field allows the new
   * types to be used in existing parsers and samplers, avoiding code duplication.
   *
   * <p>This is intentionally not exposed public as {@link HttpServerHandler} holds the
   * responsibility of passing adapters to call sites that need them.
   *
   * @since 5.7
   */
  static final HttpServerAdapter<HttpServerRequest, HttpServerResponse> LEGACY =
    new HttpServerAdapter<HttpServerRequest, HttpServerResponse>() {
      @Override public boolean parseClientIpAndPort(HttpServerRequest request, Span span) {
        if (parseClientIpFromXForwardedFor(request, span)) return true;
        return request.parseClientIpAndPort(span);
      }

      @Override public String method(HttpServerRequest request) {
        return request.method();
      }

      @Override public String url(HttpServerRequest request) {
        return request.url();
      }

      @Override public String requestHeader(HttpServerRequest request, String name) {
        return request.header(name);
      }

      @Override public String path(HttpServerRequest request) {
        return request.path();
      }

      @Override public long startTimestamp(HttpServerRequest request) {
        return request.startTimestamp();
      }

      @Override public String methodFromResponse(HttpServerResponse response) {
        return response.method();
      }

      @Override public String route(HttpServerResponse response) {
        return response.route();
      }

      @Override public int statusCodeAsInt(HttpServerResponse response) {
        return response.statusCode();
      }

      @Override public Integer statusCode(HttpServerResponse response) {
        int result = response.statusCode();
        return result > 0 ? result : null;
      }

      @Override public long finishTimestamp(HttpServerResponse response) {
        return response.finishTimestamp();
      }

      @Override public String toString() {
        return "LegacyHttpServerAdapter{}";
      }
    };

  /**
   * @deprecated {@link #parseClientIpAndPort} addresses this functionality. This will be removed in
   * Brave v6.
   */
  @Deprecated public boolean parseClientAddress(Req req, zipkin2.Endpoint.Builder builder) {
    return false;
  }

  /**
   * Used by {@link HttpServerHandler#handleReceive(HttpServerRequest)} to add remote socket
   * information about the client. By default, this tries to parse the {@link
   * #parseClientIpFromXForwardedFor(Object, Span) forwarded IP}. Override to add client socket
   * information when forwarded info is not available.
   *
   * <p>Aside: the ability to parse socket information on server request objects is likely even if
   * it is not as likely on the client side. This is because client requests are often parsed before
   * a network route is chosen, whereas server requests are parsed after the network layer.
   *
   * @since 5.2
   */
  public boolean parseClientIpAndPort(Req req, Span span) {
    return parseClientIpFromXForwardedFor(req, span);
  }

  /**
   * Returns the first value in the "X-Forwarded-For" header, or null if not present.
   *
   * @since 5.2
   */
  public boolean parseClientIpFromXForwardedFor(Req req, Span span) {
    String forwardedFor = requestHeader(req, "X-Forwarded-For");
    if (forwardedFor == null) return false;
    int indexOfComma = forwardedFor.indexOf(',');
    if (indexOfComma != -1) forwardedFor = forwardedFor.substring(0, indexOfComma);
    return span.remoteIpAndPort(forwardedFor, 0);
  }
}
