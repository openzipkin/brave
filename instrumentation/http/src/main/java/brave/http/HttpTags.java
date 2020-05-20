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

import brave.SpanCustomizer;
import brave.Tag;
import brave.propagation.TraceContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard tags used in {@linkplain HttpRequestParser request} and {@linkplain HttpResponseParser
 * response parsers}.
 *
 * @see HttpRequestParser
 * @see HttpResponseParser
 * @since 5.11
 */
public final class HttpTags {
  /** In worst case, holds 500 strings corresponding to all valid status codes. */
  static final Map<Integer, String> CACHED_STATUS_CODES = new ConcurrentHashMap<>();

  /**
   * This tags "http.method" as the value of  {@link HttpRequest#method()}, such as "GET" or
   * "POST".
   *
   * @see HttpRequest#method()
   * @see HttpRequestParser#parse(HttpRequest, TraceContext, SpanCustomizer)
   * @see HttpResponse#request()
   * @since 5.11
   */
  public static final Tag<HttpRequest> METHOD = new Tag<HttpRequest>("http.method") {
    @Override protected String parseValue(HttpRequest input, TraceContext context) {
      return input.method();
    }
  };

  /**
   * This tags "http.path" as the value of  {@link HttpRequest#path()}. Ex. "/objects/abcd-ff"
   *
   * @see HttpRequest#path()
   * @see #ROUTE
   * @see HttpRequestParser#parse(HttpRequest, TraceContext, SpanCustomizer)
   * @see HttpResponse#request()
   * @since 5.11
   */
  public static final Tag<HttpRequest> PATH = new Tag<HttpRequest>("http.path") {
    @Override protected String parseValue(HttpRequest input, TraceContext context) {
      return input.path();
    }
  };

  /**
   * This tags "http.route" as the value of {@link HttpRequest#route()}. Ex "/users/{userId}" or ""
   * (empty string) if routing is supported, but there was no match.
   *
   * @see HttpRequest#route()
   * @see #PATH
   * @see HttpRequestParser#parse(HttpRequest, TraceContext, SpanCustomizer)
   * @see HttpResponse#request()
   * @since 5.11
   */
  public static final Tag<HttpRequest> ROUTE = new Tag<HttpRequest>("http.route") {
    @Override protected String parseValue(HttpRequest input, TraceContext context) {
      return input.route();
    }
  };

  /**
   * This tags "http.url" as the value of {@link HttpRequest#url()}. Unlike {@link #PATH}, this
   * includes the scheme, host and query parameters.
   *
   * <p>Ex. "https://mybucket.s3.amazonaws.com/objects/abcd-ff?X-Amz-Algorithm=AWS4-HMAC-SHA256..."
   *
   * <p>Combined with {@link #METHOD}, you can understand the fully-qualified request line.
   *
   * <p><em>Caution:</em>This may include private data or be of considerable length.
   *
   * @see HttpRequest#url()
   * @see #PATH
   * @see HttpRequestParser#parse(HttpRequest, TraceContext, SpanCustomizer)
   * @see HttpResponse#request()
   * @since 5.11
   */
  public static final Tag<HttpRequest> URL = new Tag<HttpRequest>("http.url") {
    @Override protected String parseValue(HttpRequest input, TraceContext context) {
      return input.url();
    }
  };

  /**
   * This tags "http.status_code" as the value of {@link HttpResponse#statusCode()}, when a valid
   * status code (100 - 599).
   *
   * @see HttpResponse#statusCode()
   * @see HttpResponseParser#parse(HttpResponse, TraceContext, SpanCustomizer)
   * @since 5.11
   */
  public static final Tag<HttpResponse> STATUS_CODE = new Tag<HttpResponse>("http.status_code") {
    @Override protected String parseValue(HttpResponse input, TraceContext context) {
      int statusCode = input.statusCode();
      return statusCodeString(statusCode);
    }
  };

  /**
   * Creates a tag for the given {@linkplain HttpRequest#header(String) HTTP request header}.
   *
   * <p>Ex.
   * <pre>{@code
   * USER_AGENT = HttpTags.requestHeader("User-Agent");
   * }</pre>
   *
   * @see HttpRequest#header(String)
   * @see HttpResponse#request()
   * @since 5.11
   */
  public static Tag<HttpRequest> requestHeader(String headerName) {
    return requestHeader(headerName, headerName);
  }

  /**
   * Like {@link #requestHeader(String)}, except controls the tag key used.
   *
   * <p>Ex.
   * <pre>{@code
   * USER_AGENT = HttpTags.requestHeader("http.user_agent", "User-Agent");
   * }</pre>
   *
   * @see HttpRequest#header(String)
   * @see HttpResponse#request()
   * @since 5.11
   */
  public static Tag<HttpRequest> requestHeader(String key, String headerName) {
    return new Tag<HttpRequest>(key) {
      String name = validateNonEmpty("headerName", headerName);

      @Override protected String parseValue(HttpRequest input, TraceContext context) {
        return input.header(name);
      }
    };
  }

  static String statusCodeString(int statusCode) {
    if (statusCode < 100 || statusCode > 599) return null; // not a valid status code
    String cached = CACHED_STATUS_CODES.get(statusCode); // try to avoid allocating a string
    if (cached != null) return cached;
    String result = String.valueOf(statusCode);
    CACHED_STATUS_CODES.put(statusCode, result); // lost race is unimportant
    return result;
  }

  HttpTags() {
  }
}
