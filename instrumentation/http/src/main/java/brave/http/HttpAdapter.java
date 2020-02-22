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

import brave.internal.Nullable;
import java.net.URI;

/** @deprecated Since 5.10, use {@link HttpRequest} and {@link HttpResponse} */
@Deprecated public abstract class HttpAdapter<Req, Resp> {
  /** @see HttpRequest#method() */
  public abstract String method(Req request);

  /** @see HttpRequest#path() */
  @Nullable public String path(Req request) {
    String url = url(request);
    if (url == null) return null;
    return URI.create(url).getPath(); // TODO benchmark
  }

  /** @see HttpRequest#url() */
  @Nullable public abstract String url(Req request);

  /** @see HttpRequest#header(String) */
  @Nullable public abstract String requestHeader(Req request, String name);

  /**
   * @see HttpRequest#startTimestamp()
   * @since 5.7
   */
  public long startTimestamp(Req request) {
    return 0L;
  }

  /** @see HttpResponse#method() */
  // FromResponse suffix is needed as you can't compile methods that only differ on generic params
  @Nullable public String methodFromResponse(Resp resp) {
    return null;
  }

  /** @see HttpResponse#route() */
  // NOTE: It wasn't possible for a user to easily consume HttpServerAdapter, which is why this
  // method, while generally about the server, was pushed up to the HttpAdapter.
  @Nullable public String route(Resp response) {
    return null;
  }

  /**
   * @see HttpResponse#statusCode()
   * @see #statusCodeAsInt(Object)
   * @deprecated Since 5.7, use {@link #statusCodeAsInt(Object)} which prevents boxing.
   */
  @Deprecated @Nullable public abstract Integer statusCode(Resp response);

  /**
   * @see HttpResponse#statusCode()
   * @since 4.16
   */
  public int statusCodeAsInt(Resp response) {
    Integer maybeStatus = statusCode(response);
    return maybeStatus != null ? maybeStatus : 0;
  }

  /**
   * @see HttpResponse#finishTimestamp()
   * @since 5.7
   */
  public long finishTimestamp(Resp response) {
    return 0L;
  }

  HttpAdapter() {
  }
}
