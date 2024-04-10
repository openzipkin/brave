/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.httpclient5;

import brave.http.HttpClientResponse;
import brave.internal.Nullable;
import org.apache.hc.core5.http.HttpResponse;

final class HttpResponseWrapper extends HttpClientResponse {
  @Nullable final HttpRequestWrapper request;
  @Nullable final HttpResponse response;
  @Nullable final Throwable error;

  HttpResponseWrapper(@Nullable HttpResponse response, @Nullable HttpRequestWrapper request,
    @Nullable Throwable error) {
    this.request = request;
    this.response = response;
    this.error = error;
  }

  @Override
  @Nullable
  public Object unwrap() {
    return response;
  }

  @Override
  @Nullable
  public HttpRequestWrapper request() {
    return request;
  }

  @Override
  public Throwable error() {
    return error;
  }

  @Override
  public int statusCode() {
    if (response == null) {
      return 0;
    }
    return response.getCode();
  }
}
