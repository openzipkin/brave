/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient5;

import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpResponseInterceptor;
import org.apache.hc.core5.http.protocol.HttpContext;

class TraceContextCloseScopeInterceptor implements HttpRequestInterceptor,
  HttpResponseInterceptor {

  @Override public void process(HttpRequest httpRequest, EntityDetails entityDetails,
    HttpContext httpContext) {
    HttpClientUtils.closeScope(httpContext);
  }

  @Override public void process(HttpResponse httpResponse,
    EntityDetails entityDetails, HttpContext httpContext) {
    HttpClientUtils.closeScope(httpContext);
  }
}
