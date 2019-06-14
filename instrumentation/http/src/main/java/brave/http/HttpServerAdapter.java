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
import brave.propagation.TraceContext;

public abstract class HttpServerAdapter<Req, Resp> extends HttpAdapter<Req, Resp> {

  /**
   * @deprecated {@link #parseClientIpAndPort} addresses this functionality. This will be removed in
   * Brave v6.
   */
  @Deprecated public boolean parseClientAddress(Req req, zipkin2.Endpoint.Builder builder) {
    return false;
  }

  /**
   * Used by {@link HttpServerHandler#handleReceive(TraceContext.Extractor, Object, Object)} to add
   * remote socket information about the client. By default, this tries to parse the {@link
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
