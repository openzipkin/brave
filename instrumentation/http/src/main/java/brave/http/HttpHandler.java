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
import brave.internal.Platform;

import static brave.internal.Throwables.propagateIfFatal;

abstract class HttpHandler {
  /**
   * To avoid passing null to signatures that use HttpAdapter, we use a dummy value when {@link
   * HttpRequest#unwrap()} or {@link HttpResponse#unwrap()} return null.
   */
  static final Object NULL_SENTINEL = new Object();

  final HttpRequestParser requestParser;
  final HttpResponseParser responseParser;

  HttpHandler(HttpRequestParser requestParser, HttpResponseParser responseParser) {
    this.requestParser = requestParser;
    this.responseParser = responseParser;
  }

  Span handleStart(HttpRequest request, Span span) {
    if (span.isNoop()) return span;

    try {
      parseRequest(request, span);
    } catch (Throwable t) {
      propagateIfFatal(t);
      Platform.get().log("error parsing request {0}", request, t);
    } finally {
      // all of the above parsing happened before a timestamp on the span
      long timestamp = request.startTimestamp();
      if (timestamp == 0L) {
        span.start();
      } else {
        span.start(timestamp);
      }
    }
    return span;
  }

  void parseRequest(HttpRequest request, Span span) {
    span.kind(request.spanKind());
    requestParser.parse(request, span.context(), span.customizer());
  }

  void parseResponse(HttpResponse response, Span span) {
    responseParser.parse(response, span.context(), span.customizer());
  }

  void handleFinish(HttpResponse response, Span span) {
    if (response == null) throw new NullPointerException("response == null");
    if (span == null) throw new NullPointerException("span == null");
    if (span.isNoop()) return;

    if (response.error() != null) {
      span.error(response.error()); // Ensures MutableSpan.error() for FinishedSpanHandler
    }

    try {
      parseResponse(response, span);
    } catch (Throwable t) {
      propagateIfFatal(t);
      Platform.get().log("error parsing response {0}", response, t);
    } finally {
      long finishTimestamp = response.finishTimestamp();
      if (finishTimestamp == 0L) {
        span.finish();
      } else {
        span.finish(finishTimestamp);
      }
    }
  }
}
